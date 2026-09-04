package com.finngraph

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
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
}
