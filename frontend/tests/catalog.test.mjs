import test from 'node:test'
import assert from 'node:assert/strict'
import { healthScore } from '../src/lib/catalog.ts'

test('health score uses the fixed skin, coat and ear weights', () => {
  assert.equal(healthScore('CLEAR', 'LIGHT', 'CLEAN'), 93)
  assert.equal(healthScore('DRY', 'LIGHT', 'MILD'), 79)
  assert.equal(healthScore('CLEAR', 'SMOOTH', 'CLEAN'), 100)
  assert.equal(healthScore('OTHER', 'SMOOTH', 'CLEAN'), null)
})
