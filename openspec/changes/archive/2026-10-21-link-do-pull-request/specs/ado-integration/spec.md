## ADDED Requirements

### Requirement: A pull request's link is derived, not read from the payload
The ingestion SHALL build a pull request's deep link from what it already knows — the organisation,
the project, the repository and the pull request id — rather than reading it from a link field of
the response. The reviews of that pull request, which have no record of their own in Azure DevOps,
SHALL reuse the same link.

The list endpoint the sync reads does not carry a web link, so depending on one left every pull
request with an empty address and every drill-down row unclickable: the list exists so a team can
check a figure item by item, and an item that does not open cannot be checked. Deriving the address
also makes it the same in every environment, instead of present or absent depending on which
endpoint answered.

#### Scenario: A pull request from the list endpoint still gets a link
- **WHEN** a pull request is ingested from a response that carries no web link
- **THEN** its event records a link addressing that pull request in its repository

#### Scenario: The reviews reuse the pull request's link
- **WHEN** the reviews of that pull request are ingested
- **THEN** each review records the same link, since a review has no address of its own

#### Scenario: Repository and project names with spaces
- **WHEN** the repository or project name contains a space
- **THEN** the link is path-encoded and remains valid
