package com.engperf.bootstrap.config;

import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.LocalDate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the in-memory cadastro with the prototype's sample structure so the admin screens have
 * content. Placeholder data — replaced by the real Azure DevOps sync (S9).
 */
@Component
@Order(1)
class StructureFixtures implements CommandLineRunner {

  private static final LocalDate START = LocalDate.of(2025, 1, 1);

  private final StructureRepositoryPort repository;

  StructureFixtures(StructureRepositoryPort repository) {
    this.repository = repository;
  }

  @Override
  public void run(String... args) {
    if (!repository.findVerticals().isEmpty()) {
      return;
    }
    seedVerticalsAndTeams();
    seedPeople();
    seedRepositories();
    seedIdentities();
  }

  private void seedVerticalsAndTeams() {
    repository.saveVertical(new Vertical("v:pagamentos", "Pagamentos", "p:ana-souza"));
    repository.saveVertical(new Vertical("v:plataforma", "Plataforma", "p:eduardo-alves"));
    repository.saveVertical(new Vertical("v:growth", "Growth", "p:igor-nunes"));

    repository.saveTeam(new Team("t:checkout", "Checkout", "v:pagamentos", "p:ana-souza", null));
    repository.saveTeam(
        new Team("t:antifraude", "Antifraude", "v:pagamentos", "p:carla-dias", null));
    repository.saveTeam(
        new Team("t:core-banking", "Core Banking", "v:plataforma", "p:eduardo-alves", null));
    repository.saveTeam(new Team("t:sre", "SRE", "v:plataforma", "p:gustavo-melo", null));
    repository.saveTeam(new Team("t:aquisicao", "Aquisição", "v:growth", "p:igor-nunes", null));
    repository.saveTeam(new Team("t:retencao", "Retenção", "v:growth", "p:karina-alves", null));
  }

  private void seedPeople() {
    person("p:ana-souza", "Ana Souza", "ana.souza@empresa.com", "t:checkout");
    person("p:bruno-lima", "Bruno Lima", "bruno.lima@empresa.com", "t:checkout");
    person("p:carla-dias", "Carla Dias", "carla.dias@empresa.com", "t:antifraude");
    person("p:diego-reis", "Diego Reis", "diego.reis@empresa.com", "t:antifraude");
    person("p:eduardo-alves", "Eduardo Alves", "eduardo.alves@empresa.com", "t:core-banking");
    person("p:fernanda-rocha", "Fernanda Rocha", "fernanda.rocha@empresa.com", "t:core-banking");
    person("p:gustavo-melo", "Gustavo Melo", "gustavo.melo@empresa.com", "t:sre");
    person("p:helena-prado", "Helena Prado", "helena.prado@empresa.com", "t:sre");
    person("p:igor-nunes", "Igor Nunes", "igor.nunes@empresa.com", "t:aquisicao");
    person("p:julia-castro", "Júlia Castro", "julia.castro@empresa.com", "t:aquisicao");
    person("p:karina-alves", "Karina Alves", "karina.alves@empresa.com", "t:retencao");
    person("p:lucas-faria", "Lucas Faria", "lucas.faria@empresa.com", "t:retencao");
  }

  private void person(String id, String name, String email, String teamId) {
    repository.savePerson(Person.create(id, name, email, teamId, START));
  }

  private void seedRepositories() {
    // Sample repos across a single org here, each registered with its org + production stage.
    repo("checkout-service", "Pagamentos", "t:checkout");
    repo("pix-gateway", "Pagamentos", "t:checkout");
    repo("antifraude-api", "Pagamentos", "t:antifraude");
    repo("core-banking", "Plataforma", "t:core-banking");
    repo("sre-tooling", "Plataforma", "t:sre");
    repo("growth-web", "Growth", "t:aquisicao");
    repo("retention-jobs", "Growth", "t:retencao");
    repo("legacy-batch", "Plataforma", null);
  }

  private void repo(String key, String project, String teamId) {
    repository.saveRepository(new Repository(key, "minhaorg", project, teamId, "production"));
  }

  private void seedIdentities() {
    repository.saveIdentity(
        new CommitterIdentity("ana.souza@empresa.com", "Ana Souza", "p:ana-souza", 412));
    repository.saveIdentity(
        new CommitterIdentity(
            "asouza@users.noreply.dev.azure.com", "ana-souza", "p:ana-souza", 38));
    repository.saveIdentity(
        new CommitterIdentity("bruno.lima@empresa.com", "Bruno Lima", "p:bruno-lima", 355));
    repository.saveIdentity(
        new CommitterIdentity(
            "eduardo.alves@empresa.com", "Eduardo Alves", "p:eduardo-alves", 298));
    repository.saveIdentity(
        new CommitterIdentity("copilot@github.com", "GitHub Copilot", null, 96));
    repository.saveIdentity(
        new CommitterIdentity("azure-deploy@ci.local", "deploy-bot", null, 640));
    repository.saveIdentity(
        new CommitterIdentity("c.dias.pessoal@gmail.com", "carla d.", null, 27));
  }
}
