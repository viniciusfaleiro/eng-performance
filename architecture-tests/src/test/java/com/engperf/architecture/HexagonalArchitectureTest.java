package com.engperf.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * Enforces the hexagonal (ports & adapters) boundaries at build time. A violation fails the build,
 * so the architecture cannot silently erode.
 */
@AnalyzeClasses(
    packages = "com.engperf",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class HexagonalArchitectureTest {

  @ArchTest
  static final ArchRule layers =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Domain")
          .definedBy("com.engperf.domain..")
          .layer("Application")
          .definedBy("com.engperf.application..")
          .layer("AdaptersIn")
          .definedBy("com.engperf.adapter.inbound..")
          .layer("AdaptersOut")
          .definedBy("com.engperf.adapter.outbound..")
          .layer("Bootstrap")
          .definedBy("com.engperf.bootstrap..")
          .whereLayer("Bootstrap")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("AdaptersIn")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("AdaptersOut")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("AdaptersIn", "AdaptersOut", "Bootstrap")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Application", "AdaptersIn", "AdaptersOut", "Bootstrap");

  @ArchTest
  static final ArchRule domainIsFrameworkFree =
      com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("com.engperf.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.engperf.application..",
              "com.engperf.adapter..",
              "com.engperf.bootstrap..",
              "org.springframework..",
              "jakarta..",
              "org.hibernate..");

  @ArchTest
  static final ArchRule applicationHasNoSpring =
      com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("com.engperf.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.engperf.adapter..", "com.engperf.bootstrap..", "org.springframework..");

  @ArchTest
  static final ArchRule adaptersDoNotDependOnEachOther =
      com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("com.engperf.adapter.inbound..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.engperf.adapter.outbound..");

  @ArchTest
  static final SliceRule noCycles =
      SlicesRuleDefinition.slices().matching("com.engperf.(*)..").should().beFreeOfCycles();

  // Only the Azure DevOps outbound adapter may speak HTTP to the outside world.
  @ArchTest
  static final ArchRule onlyAdoAdapterTalksHttp =
      com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
          .that()
          .resideOutsideOfPackage("com.engperf.adapter.outbound.ado..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("java.net.http..");
}
