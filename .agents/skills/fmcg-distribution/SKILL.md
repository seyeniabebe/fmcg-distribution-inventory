---
name: fmcg-distribution
description: Domain architecture and coding rules for the FMCG Distribution Inventory, Agent Sales, and Debtor Management System in Google Antigravity.
---
# FMCG Distribution Skill for Google Antigravity

When adding features or modifying this project in Google Antigravity:
1. Keep all stock deductions and sale recordings inside `@Transactional` methods in `DistributionService.java`.
2. Never expose `buyPrice` or `unitProfit` in the Field Agent API responses or Agent UI view; restrict profit metrics to the Admin HQ Dashboard.
3. Ensure every `CREDIT` sale records `customerName`, `customerPhone`, `customerArea`, and `agentName` so the Debtors view can follow up and mark balances cleared.
