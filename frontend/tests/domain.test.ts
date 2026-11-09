import test from "node:test";
import assert from "node:assert/strict";
import {
  allocateDiscount,
  unitRefund,
  refundable,
  canReturnCoupon,
  type OrderItem,
} from "../src/domain.ts";

test("two shirts at 99, 20 coupon: one unit refunds 89; partial refund does not return coupon", () => {
  const item: OrderItem = {
    productId: 101,
    name: "T恤",
    image: "",
    spec: "炭黑/M",
    price: 9900,
    quantity: 2,
    discount: 2000,
    refunded: 0,
    returnedQuantity: 0,
  };
  assert.equal(unitRefund(item, 1), 8900);
  item.refunded = 8900;
  item.returnedQuantity = 1;
  assert.equal(unitRefund(item, 1), 8900);
  assert.equal(refundable(item), 8900);
  assert.equal(canReturnCoupon(18600, 8900), false);
});
test("discount remainders conserve cents deterministically across lines", () => {
  assert.deepEqual(
    allocateDiscount(
      [
        { price: 100, quantity: 1 },
        { price: 100, quantity: 1 },
        { price: 100, quantity: 1 },
      ],
      100,
    ),
    [34, 33, 33],
  );
  assert.deepEqual(
    allocateDiscount([{ price: 9900, quantity: 2 }], 2000),
    [2000],
  );
  assert.deepEqual(allocateDiscount([], 0), []);
});
test("individual unit remainders cannot overrefund a discounted order item", () => {
  const item: OrderItem = {
    productId: 101,
    name: "T恤",
    image: "",
    spec: "M",
    price: 100,
    quantity: 3,
    discount: 100,
    refunded: 0,
    returnedQuantity: 0,
  };
  assert.equal(unitRefund(item, 1), 66);
  item.refunded = 66;
  item.returnedQuantity = 1;
  assert.equal(unitRefund(item, 2), 134);
  assert.equal(unitRefund(item, 2) + item.refunded, 200);
});
test("invalid amounts and quantity are rejected rather than rounded into a new price", () => {
  assert.throws(() => allocateDiscount([{ price: 100, quantity: 1 }], 101));
  assert.throws(() => allocateDiscount([{ price: 100, quantity: 0 }], 1));
  assert.throws(() => allocateDiscount([{ price: 100, quantity: 1 }], 0.5));
  assert.throws(() =>
    allocateDiscount([{ price: Number.MAX_SAFE_INTEGER, quantity: 2 }], 1),
  );
});
test("large safe cent amounts use exact integer multiplication", () => {
  const result = allocateDiscount(
    [
      { price: 100000000, quantity: 1 },
      { price: 200000001, quantity: 1 },
    ],
    99999999,
  );
  assert.equal(
    result.reduce((a, b) => a + b, 0),
    99999999,
  );
  assert.ok(result.every(Number.isSafeInteger));
});
