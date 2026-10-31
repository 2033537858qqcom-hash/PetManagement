import test from 'node:test'
import assert from 'node:assert/strict'
import { bookingTiming, suggestedTime } from '../src/lib/workspace.ts'
const now = new Date(2026, 9, 29, 16, 7)

test('appointment preview includes service end and cleanup boundary', () => {
  const result = bookingTiming('2026-10-30', '19:45', 60, now)
  assert.equal(result.error, '')
  assert.equal(result.endTime, '20:45')
  assert.equal(result.cleanupEndTime, '21:00')
  assert.equal(result.latestStart, '19:45')
})
test('cleaning must fit inside store hours', () => {
  assert.match(bookingTiming('2026-10-30', '20:00', 60, now).error, /19:45/)
  assert.match(bookingTiming('2026-10-30', '08:45', 60, now).error, /09:00/)
})
test('invalid dates, empty times and past appointments are explained', () => {
  assert.match(bookingTiming('', '11:30', 60, now).error, /日期/)
  assert.match(bookingTiming('2026-02-30', '11:30', 60, now).error, /日期/)
  assert.match(bookingTiming('2026-10-30', '', 60, now).error, /时间/)
  assert.match(bookingTiming('2026-10-29', '16:00', 60, now).error, /已过/)
  assert.match(bookingTiming('2026-10-28', '17:00', 60, now).error, /已过/)
  assert.equal(bookingTiming('2026-10-29', '16:15', 60, now).error, '')
})
test('same-day defaults use the next quarter hour while future dates retain a daytime default', () => {
  assert.equal(suggestedTime('2026-10-29', now), '16:15')
  assert.equal(suggestedTime('2026-10-30', now), '11:30')
  assert.equal(suggestedTime('2026-10-29', new Date(2026,9,29,8,2)), '09:00')
})

test('timing errors identify the field that needs attention', () => {
  assert.equal(bookingTiming('', '11:30', 60, now).field, 'date')
  assert.equal(bookingTiming('2026-10-30', '20:00', 60, now).field, 'time')
  assert.equal(bookingTiming('2026-10-30', '11:30', 60, now).field, null)
})
