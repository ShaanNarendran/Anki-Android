// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.browser.CardBrowserFragment
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.pages.Statistics

/**
 * Manages the bottom navigation bar for the home screen.
 *
 * On tablets (fragmented), this is a no-op because tablets use the navigation
 * drawer with a split pane.
 */
@NeedsTest("tab switches show/hide correct fragments")
@NeedsTest("back press returns to Home tab before exiting")
context(deckPicker: DeckPicker)
fun setupBottomNavigation() {
    if (deckPicker.supportFragmentManager.findFragmentByTag(NavigationItem.BROWSER.tag) != null) {
        ensureBrowserViewModel()
    }
    if (!deckPicker.bottomNavigationEnabled) return

    val bottomNav = deckPicker.findViewById<BottomNavigationView>(R.id.bottom_navigation)
    val fragmentContainer = deckPicker.findViewById<View>(R.id.bottom_nav_fragment_container)
    val contentWrapper = deckPicker.findViewById<View>(R.id.deck_picker_content_wrapper)
    val studiedSummary = deckPicker.deckPickerBinding.reviewSummaryTextView
    // Keep scrolled deck titles from appearing behind the line once it sits above the bar.
    studiedSummary.setBackgroundColor(MaterialColors.getColor(bottomNav, com.google.android.material.R.attr.colorSurface))
    bottomNav.isVisible = true
    val scrollBehavior = HideViewOnScrollBehavior.from(bottomNav)

    fun updateFragmentBottomMargin() {
        val margin =
            if (scrollBehavior.isScrolledIn) {
                bottomNav.height
            } else {
                ViewCompat.getRootWindowInsets(bottomNav)?.getInsets(navigationBars())?.bottom ?: 0
            }
        if (fragmentContainer.marginBottom != margin) {
            fragmentContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = margin }
        }
    }

    fun positionStudiedSummary(animate: Boolean) {
        // The summary is pinned to the screen bottom inside Home, not to the bottom nav.
        // Place it above the visible bar; as the bar slides out, move it down and fade it
        // instead of leaving it readable underneath the system navigation bar.
        val translation =
            if (scrollBehavior.isScrolledIn && studiedSummary.height > 0) {
                val navLocation = IntArray(2).also(bottomNav::getLocationInWindow)
                val summaryLocation = IntArray(2).also(studiedSummary::getLocationInWindow)
                // Ignore any in-flight translations to position the text above the bar at rest.
                val navTop = navLocation[1] - bottomNav.translationY
                val summaryContentBottom =
                    summaryLocation[1] - studiedSummary.translationY + studiedSummary.height - studiedSummary.paddingBottom
                navTop - summaryContentBottom - studiedSummary.marginBottom
            } else if (scrollBehavior.isScrolledIn) {
                -bottomNav.height.toFloat()
            } else {
                0f
            }
        val alpha = if (scrollBehavior.isScrolledIn) 1f else 0f
        studiedSummary.animate().cancel()
        if (animate) {
            studiedSummary
                .animate()
                .translationY(translation)
                .alpha(alpha)
                .setDuration(bottomNav.resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
                .start()
        } else {
            studiedSummary.translationY = translation
            studiedSummary.alpha = alpha
        }
    }

    // Keep navigation available when touch exploration is in use.
    scrollBehavior.disableOnTouchExploration(true)
    scrollBehavior.addOnScrollStateChangedListener { _, _ ->
        updateFragmentBottomMargin()
        positionStudiedSummary(animate = true)
        // The summary's alpha does not affect the deck list's separate fade overlay.
        // Remove that fade while the summary and bottom bar are hidden.
        deckPicker.deckPickerBinding.decksFadeWrapper.anchorView = studiedSummary.takeIf { scrollBehavior.isScrolledIn }
        // The Home FAB and deck list use the bottom bar's height when calculating their insets.
        ViewCompat.requestApplyInsets(deckPicker.deckPickerBinding.root)
    }

    NavigationItem.populateMenu(bottomNav, deckPicker)

    // Return to Home tab on back press when on a non-Home tab
    val bottomNavBackCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                bottomNav.selectedItemId = NavigationItem.HOME.id
            }
        }
    deckPicker.onBackPressedDispatcher.addCallback(deckPicker, bottomNavBackCallback)

    // Handle system navigation bar insets for the bottom nav
    ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
        val navBars = insets.getInsets(navigationBars())
        view.updatePadding(bottom = navBars.bottom)
        insets
    }

    bottomNav.setOnItemSelectedListener { item ->
        val navItem = NavigationItem.fromId(item.itemId) ?: return@setOnItemSelectedListener false
        if (item.itemId != bottomNav.selectedItemId) {
            Analytics.sendAnalyticsScreenView(navItem.analyticsScreenName)
            scrollBehavior.slideIn(bottomNav)
        }
        handleNavigationItemSelected(navItem, contentWrapper, fragmentContainer, bottomNavBackCallback)
    }

    bottomNav.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
        updateFragmentBottomMargin()
        if (bottom - top != oldBottom - oldTop) positionStudiedSummary(animate = false)
    }
    studiedSummary.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (scrollBehavior.isScrolledIn) positionStudiedSummary(animate = false)
    }
}

context(deckPicker: DeckPicker)
fun showRestoredBottomNavTab() {
    if (!deckPicker.bottomNavigationEnabled) return
    val bottomNav = deckPicker.binding.bottomNavigation ?: return
    bottomNav.selectedItemId = bottomNav.selectedItemId
}

context(deckPicker: DeckPicker)
fun selectedBottomNavItem(): NavigationItem? {
    if (!deckPicker.bottomNavigationEnabled) return null
    val bottomNav = deckPicker.binding.bottomNavigation ?: return null
    return NavigationItem.fromId(bottomNav.selectedItemId)
}

context(deckPicker: DeckPicker)
private fun handleNavigationItemSelected(
    item: NavigationItem,
    contentWrapper: View,
    fragmentContainer: View,
    backCallback: OnBackPressedCallback,
): Boolean =
    when (item) {
        NavigationItem.HOME -> {
            fragmentContainer.isVisible = false
            deckPicker.supportFragmentManager.commit { hideBottomNavFragments() }
            contentWrapper.isVisible = true
            deckPicker.floatingActionMenu.showFloatingActionButton()
            backCallback.isEnabled = false
            true
        }
        NavigationItem.BROWSER -> {
            backCallback.isEnabled = true
            ensureBrowserViewModel()
            showBottomNavFragment(::CardBrowserFragment, item.tag, contentWrapper, fragmentContainer)
            true
        }
        NavigationItem.STATS -> {
            backCallback.isEnabled = true
            showBottomNavFragment(
                {
                    Statistics().apply {
                        arguments = Bundle().apply { putBoolean(Statistics.ARG_HIDE_BACK_BUTTON, true) }
                    }
                },
                item.tag,
                contentWrapper,
                fragmentContainer,
            )
            true
        }
        NavigationItem.MORE -> {
            backCallback.isEnabled = true
            showBottomNavFragment(::MoreFragment, item.tag, contentWrapper, fragmentContainer)
            true
        }
    }

/** Create CardBrowserViewModel before the fragment accesses it via activityViewModels() */
context(deckPicker: DeckPicker)
private fun ensureBrowserViewModel() {
    ViewModelProvider(
        deckPicker.viewModelStore,
        CardBrowserViewModel.factory(
            lastDeckIdRepository = AnkiDroidApp.instance.sharedPrefsLastDeckIdRepository,
            cacheDir = deckPicker.cacheDir,
            options = null,
            isFragmented = false,
        ),
        deckPicker.defaultViewModelCreationExtras,
    )[CardBrowserViewModel::class.java]
}

context(deckPicker: DeckPicker)
private fun showBottomNavFragment(
    newFragment: () -> Fragment,
    tag: String,
    contentWrapper: View,
    fragmentContainer: View,
) {
    contentWrapper.isVisible = false
    deckPicker.floatingActionMenu.hideFloatingActionButton()
    deckPicker.supportFragmentManager.commit {
        hideBottomNavFragments()
        val existing = deckPicker.supportFragmentManager.findFragmentByTag(tag)
        if (existing != null) {
            show(existing)
        } else {
            add(R.id.bottom_nav_fragment_container, newFragment(), tag)
        }
    }
    fragmentContainer.isVisible = true
}

/** Route scrolls from views which do not propagate nested scrolling to the Material behavior. */
fun Fragment.updateBottomNavOnScroll(dy: Int) {
    val deckPicker = activity as? DeckPicker ?: return
    if (dy == 0 || !isVisible || !deckPicker.bottomNavigationEnabled) return
    val bottomNav = deckPicker.binding.bottomNavigation ?: return
    val behavior = HideViewOnScrollBehavior.from(bottomNav)
    if (dy > 0) behavior.slideOut(bottomNav) else behavior.slideIn(bottomNav)
}

/** Hides any fragments currently hosted in the bottom-nav container. */
context(deckPicker: DeckPicker)
private fun FragmentTransaction.hideBottomNavFragments() {
    deckPicker.supportFragmentManager.fragments.forEach { fragment ->
        if (fragment.id == R.id.bottom_nav_fragment_container) hide(fragment)
    }
}
