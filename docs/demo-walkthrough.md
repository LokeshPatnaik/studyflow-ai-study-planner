# Faculty demonstration walkthrough

1. Start the application and sign in with `demo@planner.local` / `DemoPass123!`.
2. Point out the explicit demonstration-data banner, the three sample subjects, exam dates, progress metrics, one completed task, and one missed task.
3. Open **Syllabus** to show approved topics. Upload a selectable-text syllabus PDF or add topics manually; review extracted suggestions before saving.
4. Open **Agent center** and run **Generate a study plan**. Show the Observe → Analyze → Plan → Act → Evaluate → Adapt → Record history and each tool result. Without `OPENAI_API_KEY`, the run reports Rule-based mode. With a valid key, the run reports OpenAI function tools.
5. Review the resulting draft plan from the task list or Overview. Approve only after reviewing the dates and durations.
6. Mark a planned session complete and mark one missed.
7. Run **Adapt around missed or unfinished work**. Open **Schedule revisions** to compare the current and proposed dates/durations.
8. Approve or reject the proposal. Approval applies the candidate; rejection leaves the current schedule unchanged.
9. Show Analytics and verify that totals follow the task records.

The seeded tasks and exams use dates relative to the local startup date. Reset the local demo database volume only when you intentionally want to recreate the seed from scratch.

