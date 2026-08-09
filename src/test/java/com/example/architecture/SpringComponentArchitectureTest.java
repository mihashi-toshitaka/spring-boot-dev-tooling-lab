package com.example.architecture;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.example")
class SpringComponentArchitectureTest {

    @ArchTest
    static final ArchRule PAGE_CONTROLLERS_SHOULD_RESIDE_IN_PAGE_PACKAGE =
            classes().that().areAnnotatedWith(Controller.class).should().resideInAPackage("..page.controller..");

    @ArchTest
    static final ArchRule REST_CONTROLLERS_SHOULD_RESIDE_IN_API_PACKAGE =
            classes().that().areAnnotatedWith(RestController.class).should().resideInAPackage("..api.controller..");

    @ArchTest
    static final ArchRule SERVICES_SHOULD_RESIDE_IN_FEATURE_SERVICE_PACKAGES = classes()
            .that()
            .areAnnotatedWith(Service.class)
            .should()
            .resideInAnyPackage("..api.service..", "..page.service..");

    @ArchTest
    static final ArchRule SERVICE_AND_COMPONENT_FIELDS_SHOULD_BE_FINAL = fields().that()
            .areDeclaredInClassesThat(annotatedWith(Controller.class)
                    .or(annotatedWith(RestController.class))
                    .or(annotatedWith(Service.class))
                    .or(annotatedWith(Component.class)))
            .should()
            .beFinal();
}
