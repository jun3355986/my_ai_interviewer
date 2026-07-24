---
status: accepted
---

# Inherit prefix assessments across interview forks

A child Interview Branch will reuse completed assessments strictly before its Fork Point without re-running AI scoring, while the answer that starts the new path and every downstream answer receive new assessments. The child produces its own final evaluation from the inherited prefix plus its new assessments; this preserves the meaning of the shared history without introducing model-driven score drift or evaluating only the shortened suffix.
