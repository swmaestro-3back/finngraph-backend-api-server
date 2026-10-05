package com.finngraph.news.port

import com.finngraph.news.model.IssueArticle
import com.finngraph.news.model.IssueCluster
import com.finngraph.news.model.IssueMention
import com.finngraph.news.model.IssueNeighbors
import java.time.OffsetDateTime

interface NewsIssuePort {

    fun findLatestPublishedAt(): OffsetDateTime?
    fun findPublishedBetween(from: OffsetDateTime, until: OffsetDateTime): List<IssueCluster>
    fun findNeighbors(before: OffsetDateTime, from: OffsetDateTime): IssueNeighbors
    fun findById(id: Long): IssueCluster?
    fun findByIds(ids: List<Long>): List<IssueCluster>
    fun findArticles(clusterIds: List<Long>): Map<Long, List<IssueArticle>>
    fun findMentionedBetween(tickers: Collection<String>, from: OffsetDateTime, until: OffsetDateTime): List<IssueMention>
}
