## MODIFIED Requirements

### Requirement: The panel states what the reported hours are
The individual panel SHALL state that the hours reported in the work distribution are **elapsed**
hours during which an item was in progress, not hours a person worked, and that the hours of a single
item are **divided among the items in progress at the same time**. Without both statements a total is
read as effort spent and a per-item figure is read as wrong.

For an item that has not reached a terminal state, the elapsed hours SHALL run to the **clock of the
reading**, not the clock of the ingestion that recorded the item. Ending at the ingestion clock made
an open item's hours stop at the last sync, so the same item was in progress for two different
lengths depending on which figure of the panel was being read.

#### Scenario: The nature of the total is stated
- **WHEN** the work distribution is shown with a total in hours
- **THEN** the panel states that these are elapsed hours in progress, not hours worked

#### Scenario: The division is stated where per-item hours appear
- **WHEN** a list of items with their hours is shown
- **THEN** it states that concurrent items share each elapsed hour

#### Scenario: An open item's hours reach the moment of reading
- **WHEN** an item is still in progress and the last ingestion ran earlier
- **THEN** its elapsed hours run to the moment of the reading, not to the last ingestion
