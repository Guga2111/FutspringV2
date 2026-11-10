package com.futspring.backend.domain;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

/** The package layout rule of BACKEND.md ("Where a class goes"). */
class DomainLayoutTest {

    private static final int MAX_CLASSES_PER_PACKAGE = 12;

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.futspring.backend.domain");

    @Test
    void noDomainPackageHoldsMoreThanTwelveClasses() {
        Map<String, Long> oversized = CLASSES.stream()
                .filter(JavaClass::isTopLevelClass)
                .map(JavaClass::getPackageName)
                .filter(pkg -> !pkg.endsWith(".dto"))
                .collect(Collectors.groupingBy(pkg -> pkg, TreeMap::new, Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > MAX_CLASSES_PER_PACKAGE)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));

        assertThat(oversized)
                .as("packages over %d classes: split them as BACKEND.md says", MAX_CLASSES_PER_PACKAGE)
                .isEmpty();
    }

    // dto/ is left out: XDTO.from(entity) plus a service returning the DTO form a cycle by design
    @Test
    void subPackagesOfADomainAreFreeOfCycles() {
        slices().matching("com.futspring.backend.domain.(*).(*)..")
                .should().beFreeOfCycles()
                .check(CLASSES.that(not(resideInAPackage("..dto.."))));
    }
}
