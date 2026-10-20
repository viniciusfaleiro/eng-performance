## ADDED Requirements

### Requirement: Every list of considered items is paged
Any list the product shows of the **items considered** behind a figure SHALL be presented **ten at a
time**, with navigation between pages and the reader's position in the list visible — which page of
how many, over how many items in total.

The reason is what the list is for. It exists so a reader can check a number item by item, and a
month of work renders hundreds of rows into a side drawer: the reader scrolls without a landmark,
cannot return to a row they just saw, and ends up trusting or distrusting the figure wholesale
instead of checking it. Ten rows is a page someone can actually read before deciding.

Paging SHALL change **how many items are shown at once, never which items count**. The total number
of items SHALL stay stated, and the list SHALL still reconcile with the figure it opens from.

A list that was **capped** before reaching the screen SHALL say so where the navigation is shown,
and SHALL NOT offer pages beyond what it received. A truncated list that presents itself as complete
is worse than a long one: the reader reaches the last page and concludes the figure is wrong.

#### Scenario: A long list opens on its first page
- **WHEN** a metric's drawer lists 57 considered items
- **THEN** the first ten are shown, with the position and the total of 57 visible

#### Scenario: The reader moves between pages
- **WHEN** the reader advances a page
- **THEN** the next ten items are shown and the position follows

#### Scenario: A short list shows no navigation
- **WHEN** a list has ten items or fewer
- **THEN** every item is shown and no page navigation is offered

#### Scenario: Paging does not change the figure
- **WHEN** a list is paged
- **THEN** the counted items and the figure the list explains are exactly what they were

#### Scenario: A capped list says it was capped
- **WHEN** the list reaching the screen was truncated before it got there
- **THEN** the navigation states that it is partial, and offers no page beyond what arrived
