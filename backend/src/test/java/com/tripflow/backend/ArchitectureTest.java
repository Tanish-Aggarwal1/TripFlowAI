package com.tripflow.backend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Makes the layered architecture documented in {@code CLAUDE.md}/{@code README.md}
 * self-enforcing (SCRUM-219 / AUDIT-10) — {@code controller -> service -> repository ->
 * domain}, plus {@code dto/mapper/security/config/client/exception/ai}. See
 * {@code docs/architecture.md} for the rule set and rationale.
 *
 * <p>Every rule here passes against current {@code main} with no production-code changes.
 * A rule that would fail today is deliberately left out rather than shipped red.
 *
 * <p>Known deliberate exception: {@link com.tripflow.backend.service.TripOwnershipService}
 * lives in {@code service/} as a cross-cutting access-control concern shared by
 * {@code TripService}/{@code StopService}/{@code RouteOptimizationService}/
 * {@code AiItineraryService}. It is an ordinary service class — none of the rules below
 * need to special-case it, so it isn't called out further.
 */
@AnalyzeClasses(packages = "com.tripflow.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllers_must_not_access_repositories_directly = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("controllers must go through a service, never a repository directly");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..dto..", "..controller..", "..service..")
            .because("domain entities are the innermost layer and must not know about DTOs, controllers, or services");

    @ArchTest
    static final ArchRule dto_must_not_depend_on_domain_entities = noClasses()
            .that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().resideInAPackage("com.tripflow.backend.domain")
            .because("DTOs are the wire contract and must not leak JPA entities — "
                    + "domain.enums (a separate package) are fine and are used deliberately");

    @ArchTest
    static final ArchRule client_classes_must_not_be_accessed_from_controllers = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..client..")
            .because("external API clients are a service-layer concern; controllers must go through a service");

    @ArchTest
    static final ArchRule only_exception_package_may_be_a_controller_advice = classes()
            .that().areAnnotatedWith(RestControllerAdvice.class)
            .should().resideInAPackage("..exception..")
            .because("GlobalExceptionHandler is the single source of HTTP error mapping");

    @ArchTest
    static final ArchRule services_must_not_have_http_concerns = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.servlet..", "org.springframework.http..")
            .because("the service layer must stay free of HTTP-specific types — that's the controller's job");
}
