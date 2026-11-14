import { expect, test, type Page } from '@playwright/test';

/**
 * E2E: 접속 → 대기 등록 → 순번 표시 → 입장 전이 → 새로고침 복구 → 취소 (Phase 3 게이트, WUI-001/002, QUE-011~013).
 * e2e 프로파일: demo/main, concurrency-limit 1, admission interval 500ms, lease 3s, SSE interval 500ms.
 */
const ROOM = '/demo/main';

async function openRoom(page: Page) {
  await page.goto(ROOM);
  await expect(page.getByRole('heading', { name: '대기실' })).toBeVisible();
}

test('첫 사용자는 동시 입장 1명 정책으로 곧바로 입장 안내를 받는다', async ({ page }) => {
  await openRoom(page);
  await expect(page.getByRole('status')).toHaveText(/입장 차례입니다/, { timeout: 10_000 });
});

test('두 번째 사용자는 순번 1을 보고, 앞 사용자 lease 만료 후 입장한다', async ({ browser }) => {
  const first = await browser.newContext();
  const second = await browser.newContext();
  const p1 = await first.newPage();
  const p2 = await second.newPage();
  await openRoom(p1);
  await expect(p1.getByRole('status')).toHaveText(/입장 차례입니다/, { timeout: 10_000 });

  await openRoom(p2);
  await expect(p2.getByTestId('position')).toHaveText('1');
  // 첫 사용자의 lease(3s) 가 heartbeat 없이 만료되면(스트림 종료) 두 번째가 입장한다.
  await p1.close();
  await first.close();
  await expect(p2.getByRole('status')).toHaveText(/입장 차례입니다/, { timeout: 15_000 });
  await second.close();
});

test('새로고침해도 같은 쿠키로 세션이 복구되어 새 세션이 생기지 않는다', async ({ browser }) => {
  const holder = await browser.newContext();
  const hp = await holder.newPage();
  await openRoom(hp); // 입장 슬롯을 점유

  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await openRoom(page);
  await expect(page.getByTestId('position')).toHaveText('1');
  await page.reload();
  await expect(page.getByTestId('position')).toHaveText('1');
  await expect(page.getByText(/대기 인원 1명/)).toBeVisible();

  await page.getByRole('button', { name: '대기 취소' }).click();
  await expect(page.getByRole('status')).toHaveText(/취소했습니다/);
  await ctx.close();
  await holder.close();
});

test('세션 쿠키는 HttpOnly 이며 document.cookie 로 읽을 수 없다 (D-API-001)', async ({ page, context }) => {
  await openRoom(page);
  await expect(page.getByRole('status')).toBeVisible({ timeout: 10_000 });
  const cookies = await context.cookies();
  const session = cookies.find((c) => c.name === 'oq_session');
  expect(session?.httpOnly).toBe(true);
  expect(session?.sameSite).toBe('Lax');
  expect(await page.evaluate(() => document.cookie)).not.toContain('oq_session');
});
