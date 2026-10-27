Segmented progress: the onboarding steps, and how far through the review queue you are.

**You provide:** `current` (filled segments), `total`, `tone` and, where the default doesn't fit, a `label` for screen readers.

- Onboarding (`tone="light"`): three segments in the header between the back button and an equal spacer, so the bar is centred. No "Step 2 of 3" text on screen.
- Review (`tone="onSlate"`): one segment per transaction in the queue; done segments are sand. Put the count ("3/6") at the header's right.
- Welcome is not a step; the bar starts on Allow SMS.
