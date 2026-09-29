package com.finngraph.dailybriefing

import com.finngraph.composition.briefing.HeadlineDraft
import com.finngraph.composition.briefing.SentenceDraft
import com.finngraph.composition.briefing.WatchDraft
import com.finngraph.composition.port.BriefingWriterPort
import com.finngraph.composition.port.CommentaryInput
import com.finngraph.composition.port.HeadlineInput
import com.finngraph.composition.port.WatchInput

class DisabledBriefingWriter : BriefingWriterPort {

    override val enabled: Boolean = false

    override fun headline(input: HeadlineInput): HeadlineDraft? = null
    override fun commentary(input: CommentaryInput): List<SentenceDraft>? = null
    override fun watchPoints(input: WatchInput): List<WatchDraft>? = null
}
