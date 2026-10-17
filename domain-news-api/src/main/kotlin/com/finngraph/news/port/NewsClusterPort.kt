package com.finngraph.news.port

import com.finngraph.news.model.ClusterArticle
import com.finngraph.news.model.NewsCluster
import java.time.OffsetDateTime

interface NewsClusterPort {

    fun findRecent(since: OffsetDateTime, minMembers: Int, limit: Int): List<NewsCluster>
    fun findArticles(clusterIds: List<Long>, perCluster: Int): Map<Long, List<ClusterArticle>>
}
