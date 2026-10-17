package com.finngraph.config

import org.jooq.DSLContext
import org.jooq.ExecuteListenerProvider
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultExecuteListenerProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.jooq.autoconfigure.ExceptionTranslatorExecuteListener
import org.springframework.boot.jooq.autoconfigure.SpringTransactionProvider
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

internal fun jooqDslContext(
    dataSource: DataSource,
    transactionManager: PlatformTransactionManager,
    executeListenerProviders: ObjectProvider<ExecuteListenerProvider>,
    settings: ObjectProvider<Settings>,
): DSLContext {
    val configuration = DefaultConfiguration()
        .set(SQLDialect.POSTGRES)
        .set(DataSourceConnectionProvider(TransactionAwareDataSourceProxy(dataSource)))
        .set(SpringTransactionProvider(transactionManager))

    settings.ifAvailable { configuration.set(it) }

    val providers = buildList {
        add(DefaultExecuteListenerProvider(ExceptionTranslatorExecuteListener.DEFAULT))
        addAll(executeListenerProviders.orderedStream().toList())
    }
    configuration.set(*providers.toTypedArray())

    return DSL.using(configuration)
}

internal fun requireJdbcUrl(url: String, property: String): String {
    require(url.startsWith("jdbc:")) { "${property}가 jdbc URL이 아님: $url" }
    return url
}
