/* Copyright 2019 Joel Pyska
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.components.report.adapter

import android.text.Spanned
import android.text.TextUtils
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.report.model.StatusViewState
import com.keylesspalace.tusky.databinding.ItemReportStatusBinding
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.util.*
import com.keylesspalace.tusky.util.StatusViewHelper.Companion.COLLAPSE_INPUT_FILTER
import com.keylesspalace.tusky.util.StatusViewHelper.Companion.NO_INPUT_FILTER
import com.keylesspalace.tusky.viewdata.toViewData
import java.util.*

class StatusViewHolder(
        private val binding: ItemReportStatusBinding,
        private val statusDisplayOptions: StatusDisplayOptions,
        private val viewState: StatusViewState,
        private val adapterHandler: AdapterHandler,
        private val getStatusForPosition: (Int) -> Status?
) : RecyclerView.ViewHolder(binding.root) {
    private val mediaViewHeight = itemView.context.resources.getDimensionPixelSize(R.dimen.status_media_preview_height)
    private val statusViewHelper = StatusViewHelper(itemView)

    private val previewListener = object : StatusViewHelper.MediaPreviewListener {
        override fun onViewMedia(v: View?, idx: Int) {
            status()?.let { status ->
                adapterHandler.showMedia(v, status, idx)
            }
        }

        override fun onContentHiddenChange(isShowing: Boolean) {
            status()?.id?.let { id ->
                viewState.setMediaShow(id, isShowing)
            }
        }
    }

    init {
        binding.statusSelection.setOnCheckedChangeListener { _, isChecked ->
            status()?.let { status ->
                adapterHandler.setStatusChecked(status, isChecked)
            }
        }
        binding.statusMediaPreviewContainer.clipToOutline = true
    }

    fun bind(status: Status) {
        binding.statusSelection.isChecked = adapterHandler.isStatusChecked(status.id)

        updateTextView()

        val sensitive = status.sensitive

        statusViewHelper.setMediasPreview(statusDisplayOptions, status.attachments,
                sensitive, previewListener, viewState.isMediaShow(status.id, status.sensitive),
                mediaViewHeight)

        statusViewHelper.setupPollReadonly(status.poll.toViewData(), status.emojis, statusDisplayOptions)
        setCreatedAt(status.createdAt)
    }

    private fun updateTextView() {
        status()?.let { status ->
            setupCollapsedState(shouldTrimStatus(status.content), viewState.isCollapsed(status.id, true),
                    viewState.isContentShow(status.id, status.sensitive), status.spoilerText)

            if (status.spoilerText.isBlank()) {
                setTextVisible(true, status.content, status.mentions, status.emojis, adapterHandler)
                binding.statusContentWarningButton.hide()
                binding.statusContentWarningDescription.hide()
            } else {
                val emojiSpoiler = status.spoilerText.emojify(status.emojis, binding.statusContentWarningDescription, statusDisplayOptions.animateEmojis)
                binding.statusContentWarningDescription.text = emojiSpoiler
                binding.statusContentWarningDescription.show()
                binding.statusContentWarningButton.show()
                setContentWarningButtonText(viewState.isContentShow(status.id, true))
                binding.statusContentWarningButton.setOnClickListener {
                    status()?.let { status ->
                        val contentShown = viewState.isContentShow(status.id, true)
                        binding.statusContentWarningDescription.invalidate()
                        viewState.setContentShow(status.id, !contentShown)
                        setTextVisible(!contentShown, status.content, status.mentions, status.emojis, adapterHandler)
                        setContentWarningButtonText(!contentShown)
                    }
                }
                setTextVisible(viewState.isContentShow(status.id, true), status.content, status.mentions, status.emojis, adapterHandler)
            }
        }
    }

    private fun setContentWarningButtonText(contentShown: Boolean) {
        if(contentShown) {
            binding.statusContentWarningButton.setText(R.string.status_content_warning_show_less)
        } else {
            binding.statusContentWarningButton.setText(R.string.status_content_warning_show_more)
        }
    }

    private fun setTextVisible(expanded: Boolean,
                               content: Spanned,
                               mentions: Array<Status.Mention>?,
                               emojis: List<Emoji>,
                               listener: LinkListener) {
        if (expanded) {
            val emojifiedText = content.emojify(emojis, binding.statusContent, statusDisplayOptions.animateEmojis)
            LinkHelper.setClickableText(binding.statusContent, emojifiedText, mentions, listener)
        } else {
            LinkHelper.setClickableMentions(binding.statusContent, mentions, listener)
        }
        if (binding.statusContent.text.isNullOrBlank()) {
            binding.statusContent.hide()
        } else {
            binding.statusContent.show()
        }
    }

    private fun setCreatedAt(createdAt: Date?) {
        if (statusDisplayOptions.useAbsoluteTime) {
            binding.timestampInfo.text = statusViewHelper.getAbsoluteTime(createdAt)
        } else {
            binding.timestampInfo.text = if (createdAt != null) {
                val then = createdAt.time
                val now = System.currentTimeMillis()
                TimestampUtils.getRelativeTimeSpanString(binding.timestampInfo.context, then, now)
            } else {
                // unknown minutes~
                "?m"
            }
        }
    }

    private fun setupCollapsedState(collapsible: Boolean, collapsed: Boolean, expanded: Boolean, spoilerText: String) {
        /* input filter for TextViews have to be set before text */
        if (collapsible && (expanded || TextUtils.isEmpty(spoilerText))) {
            binding.buttonToggleContent.setOnClickListener{
                status()?.let { status ->
                    viewState.setCollapsed(status.id, !collapsed)
                    updateTextView()
                }
            }

            binding.buttonToggleContent.show()
            if (collapsed) {
                binding.buttonToggleContent.setText(R.string.status_content_show_more)
                binding.statusContent.filters = COLLAPSE_INPUT_FILTER
            } else {
                binding.buttonToggleContent.setText(R.string.status_content_show_less)
                binding.statusContent.filters = NO_INPUT_FILTER
            }
        } else {
            binding.buttonToggleContent.hide()
            binding.statusContent.filters = NO_INPUT_FILTER
        }
    }

    private fun status() = getStatusForPosition(adapterPosition)
}