// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.ichi2.anki.databinding.FragmentMoreBinding
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.dialogs.help.childHelpMenuItems
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.IntentUtil
import dev.androidbroadcast.vbpd.viewBinding

/**
 * Full-screen "More" destination in the bottom navigation bar.
 * Shows Settings, Help items, and Support items in a sectioned list.
 *
 * Help items open the existing [HelpDialog] focused on their sub-section.
 * Support items directly open their respective URLs.
 */
class MoreFragment : Fragment(R.layout.fragment_more) {
    private val binding by viewBinding(FragmentMoreBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Settings
        binding.moreSettings.setOnClickListener {
            startActivity(PreferencesActivity.getIntent(requireContext()))
        }

        // Help section: each item opens HelpDialog focused on its children
        binding.moreHelpManual.setOnClickListener {
            openHelpSection(R.string.help_title_using_ankidroid, parentId = 1)
        }
        binding.moreHelpGetHelp.setOnClickListener {
            openHelpSection(R.string.help_title_get_help, parentId = 2)
        }
        binding.moreHelpCommunity.setOnClickListener {
            openHelpSection(R.string.help_title_community, parentId = 3)
        }
        binding.moreHelpPrivacy.setOnClickListener {
            openHelpSection(R.string.help_title_privacy, parentId = 4)
        }

        // Support section: direct URL actions
        binding.moreSupportDonate.setOnClickListener {
            openUrl(getString(R.string.link_opencollective_donate))
        }
        binding.moreSupportTranslate.setOnClickListener {
            openUrl(getString(R.string.link_translation))
        }
        binding.moreSupportDevelop.setOnClickListener {
            openUrl(getString(R.string.link_ankidroid_development_guide))
        }
        binding.moreSupportRate.setOnClickListener {
            val intent = AnkiDroidApp.getMarketIntent(requireContext())
            if (IntentUtil.canOpenIntent(requireContext(), intent)) {
                startActivity(intent)
            }
        }
        binding.moreSupportOther.setOnClickListener {
            openUrl(getString(R.string.link_contribution))
        }
        binding.moreSupportFeedback.setOnClickListener {
            openUrl(AnkiDroidApp.feedbackUrl)
        }

        // Hide rate option if Play Store is not available
        if (!IntentUtil.canOpenIntent(requireContext(), AnkiDroidApp.getMarketIntent(requireContext()))) {
            binding.moreSupportRate.visibility = View.GONE
        }
    }

    private fun openHelpSection(
        titleRes: Int,
        parentId: Long,
    ) {
        val children = childHelpMenuItems.filter { it.parentId == parentId }
        val dialog =
            HelpDialog().apply {
                arguments =
                    bundleOf(
                        "arg_menu_title" to titleRes,
                        "arg_menu_items" to children.toTypedArray(),
                    )
            }
        requireActivity().showDialogFragment(dialog)
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        startActivity(intent)
    }
}
