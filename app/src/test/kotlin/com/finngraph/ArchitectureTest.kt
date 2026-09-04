package com.finngraph

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.springframework.transaction.annotation.Transactional

@AnalyzeClasses(packages = ["com.finngraph"], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {

    @ArchTest
    val securityIsIndependentOfWeb = noClasses()
        .that().resideInAPackage("com.finngraph.security..")
        .should().dependOnClassesThat().resideInAPackage("com.finngraph.web..")

    @ArchTest
    val webDoesNotTouchWritePorts = noClasses()
        .that().resideInAPackage("com.finngraph.web..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.finngraph.user.port..", "com.finngraph.auth.port..")

    @ArchTest
    val webDeclaresNoTransactions = methods()
        .that().areDeclaredInClassesThat().resideInAPackage("com.finngraph.web..")
        .should().notBeAnnotatedWith(Transactional::class.java)

    @ArchTest
    val webClassesDeclareNoTransactions = noClasses()
        .that().resideInAPackage("com.finngraph.web..")
        .should().beAnnotatedWith(Transactional::class.java)

    @ArchTest
    val transactionalMethodsSpecifyManager = methods()
        .that().areAnnotatedWith(Transactional::class.java)
        .should(
            object : ArchCondition<JavaMethod>("transactionManager를 명시한다") {
                override fun check(method: JavaMethod, events: ConditionEvents) {
                    val tx = method.getAnnotationOfType(Transactional::class.java)
                    if (tx.transactionManager.isEmpty() && tx.value.isEmpty()) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                "${method.fullName} — 트랜잭션 매니저가 2개(etl/app)라 @Transactional에 transactionManager 명시가 필요하다",
                            ),
                        )
                    }
                }
            },
        )

    @ArchTest
    val transactionalClassesSpecifyManager = classes()
        .that().areAnnotatedWith(Transactional::class.java)
        .should(
            object : ArchCondition<JavaClass>("transactionManager를 명시한다") {
                override fun check(clazz: JavaClass, events: ConditionEvents) {
                    val tx = clazz.getAnnotationOfType(Transactional::class.java)
                    if (tx.transactionManager.isEmpty() && tx.value.isEmpty()) {
                        events.add(
                            SimpleConditionEvent.violated(
                                clazz,
                                "${clazz.name} — 트랜잭션 매니저가 2개(etl/app)라 @Transactional에 transactionManager 명시가 필요하다",
                            ),
                        )
                    }
                }
            },
        )
        .allowEmptyShould(true)
}
