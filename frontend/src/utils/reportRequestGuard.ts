/** Generation ownership prevents late responses from replacing another task's report. */
export function createReportRequestGuard() {
  let generation = 0;
  return {
    invalidate() { generation++; },
    begin(eventId: number, title: string) {
      const mine = ++generation;
      return { eventId, title, isCurrent: () => mine === generation };
    },
  };
}
