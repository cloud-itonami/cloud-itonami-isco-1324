# cloud-itonami-isco-1324

Open Business Blueprint for **ISCO-08 1324**: Supply, Distribution and Related Managers — an ISCO
**Wave 1 (design & governance)** occupation per ADR-2607121000. This
is the THIRD wave-1 blueprint batch: management/professional work is
cognitive, **no robotics gate** — eligible for actor implementation
now.

**Maturity: `:implemented`** — SupplyDistributionManagersAdvisor ⊣
SupplyDistributionManagersGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
13 tests / 27 assertions green.

The logistics HARD invariants — arithmetic and set membership, not a
shipping preference:

1. **Stock arithmetic** — the proposed allocation quantity must not
   exceed the sku's registered on-hand stock (you cannot allocate what
   does not exist).
2. **Carrier membership** — the proposed carrier must be a member of
   the sku's registered approved-carriers set (no invented or
   unapproved carrier) — carrier approval is traceability.

Also HARD: unregistered/foreign sku, unregistered organization,
non-`:propose` effect. Escalations (always human sign-off):
`:approve-cross-border-shipment` (customs/regulatory exposure), low
confidence (< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
