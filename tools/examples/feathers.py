#!/usr/bin/env python3
"""Kill chickens, take the feathers, bank them when the pack is full.

This is a whole gathering bot with no compiled script behind it: every
decision here happens in this file, over the Agent socket, while the client
just sits there being a client. It exists as the worked example of what that
socket is for -- the same shape covers any gather-and-bank job.

    tools/examples/feathers.py --target 500

Two things about it are worth copying into anything else you drive this way.

First, an npc index is only true for the tick you read it on: the client
renumbers what it can see every time the server tells it something. So the
index is looked up and used in the same breath, and a failed attack is
retried from a fresh look rather than from the stale number.

Second, nothing here sleeps and hopes. Every action waits on a condition the
game can actually confirm -- in combat, done walking, bank open -- with a
deadline in case the answer never comes. A bot built on timers works right up
until the server is having a slow minute.
"""

import argparse
import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from agent import Agent

CHICKEN = 3
FEATHER = 381
BANKER = 95
FARM = (272, 605)
BANK = (282, 566)

# The way from the chicken farm to the bank, every fifth tile.
#
# Not a straight line and not eyeballed: it is a breadth-first search over the
# server's own collision map, sampled. Two things had to be learned the hard
# way to get this list right.
#
# Handing the client the far end and hoping does not work -- it path finds a
# short way and stops. And reducing the path to its corners does not work
# either, because a corner can be further away than the client can currently
# see: isReachable() goes false for anything outside the loaded scene, and the
# walk is simply refused. Sampling keeps every hop inside the client's own
# view, which is the only place its path finder has an opinion.
ROUTE = [(274, 602), (274, 597), (274, 592), (274, 587), (274, 582),
         (276, 579), (281, 579), (281, 574), (286, 574), (286, 571),
         (286, 566), (283, 566)]


def wait_for(a, test, secs=20.0, step=0.6):
    """Poll state until test(state) or the deadline. Returns the last state."""
    end = time.time() + secs
    st = a.call("state")["result"]
    while time.time() < end and not test(st):
        time.sleep(step)
        st = a.call("state")["result"]
    return st


def count(a, item):
    st = a.call("state")["result"]
    return sum(slot[2] for slot in st["inv"] if slot[1] == item)


def nudge(a, x, y, spread=2):
    """The nearest tile to (x, y) the client will actually walk to.

    The route comes from the server's collision map and the walking is done
    by the client, and the two do not agree tile for tile -- scenery the
    server does not model as solid still stops the client dead. Rather than
    argue with either of them, ask the client whether it will go there and
    slide the target a tile or two if it will not.
    """
    if a.call("isReachable", x, y)["result"]:
        return x, y
    for r in range(1, spread + 1):
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                if max(abs(dx), abs(dy)) != r:
                    continue
                if a.call("isReachable", x + dx, y + dy)["result"]:
                    return x + dx, y + dy
    return x, y


def walk(a, x, y, secs=40.0):
    """Walk to one nearby tile and confirm we got there."""
    x, y = nudge(a, x, y)
    end = time.time() + secs
    stuck, last = 0, None
    while time.time() < end:
        st = a.call("state")["result"]
        here = (st["x"], st["y"])
        if abs(here[0] - x) + abs(here[1] - y) <= 1:
            return True
        if here == last:
            stuck += 1
            if stuck >= 4:
                return False
        else:
            stuck = 0
        last = here
        # A walkTo onto the tile you already stand on is discarded, so only
        # ever ask for somewhere else.
        if here != (x, y):
            a.call("walkTo", x, y)
        time.sleep(1.5)
    return False


def walk_route(a, route):
    """Follow a corner list, nearest corner first, to whichever end is far.

    Entering partway along matters: a run that banks and comes back does not
    restart from the farm, and a bot that logs in somewhere unexpected should
    still be able to join the route rather than give up.
    """
    st = a.call("state")["result"]
    here = (st["x"], st["y"])
    nearest = min(range(len(route)),
                  key=lambda i: abs(route[i][0] - here[0]) + abs(route[i][1] - here[1]))
    for i in range(nearest, len(route)):
        if not walk(a, *route[i]):
            print("stuck heading for %s" % (route[i],))
            return False
    return True


def kill_one(a):
    """Engage the nearest chicken and stay out of the way until it dies."""
    for _ in range(6):
        found = a.call("getNpcById", CHICKEN)["result"]
        if not found or found[0] == -1:
            return False
        a.call("attackNpc", found[0])
        st = wait_for(a, lambda s: s["combat"], secs=4.0, step=0.4)
        if st["combat"]:
            # Hands off while fighting: re-clicking attack only re-aims, and
            # walking gets undone by the client walking back to its target.
            wait_for(a, lambda s: not s["combat"], secs=60.0)
            return True
    return False


def loot(a, radius=4):
    """Pick up every feather we can see, one at a time.

    Re-read after each pickup: taking one item renumbers the ground list, so
    a cached list would start reaching for things that have moved.
    """
    taken = 0
    while True:
        items = a.call("scene", radius)["result"]["item"]
        feathers = [i for i in items if i[0] == FEATHER]
        if not feathers:
            return taken
        _, x, y = feathers[0]
        before = count(a, FEATHER)
        a.call("pickupItem", FEATHER, x, y)
        wait_for(a, lambda s: sum(v[2] for v in s["inv"] if v[1] == FEATHER) > before,
                 secs=6.0, step=0.4)
        if count(a, FEATHER) <= before:
            return taken
        taken += 1


def bank(a):
    """Walk to the bank and deposit the feathers."""
    if not walk_route(a, ROUTE):
        print("could not reach the bank")
        return False
    for _ in range(5):
        found = a.call("getNpcByIdNotTalk", BANKER)["result"]
        if not found or found[0] == -1:
            time.sleep(1.5)
            continue
        a.call("talkToNpc", found[0])
        st = wait_for(a, lambda s: s["banking"], secs=12.0)
        if st["banking"]:
            a.call("deposit", FEATHER, count(a, FEATHER))
            time.sleep(1.5)
            a.call("closeBank")
            return True
    print("banker would not open")
    return False


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--target", type=int, default=100, help="feathers to bank")
    p.add_argument("--port", type=int, default=43800)
    opt = p.parse_args()

    a = Agent(port=opt.port)
    banked, started = 0, time.time()
    try:
        while banked < opt.target:
            st = a.call("state")["result"]
            if st["empty"] <= 2:
                if not bank(a):
                    break
                banked += 1  # feathers stack, so one deposit is the whole load
                if not walk_route(a, ROUTE[::-1]):
                    break
                continue
            if not kill_one(a):
                walk(a, *FARM)
                continue
            got = loot(a)
            have = count(a, FEATHER)
            print("+%d feather(s), carrying %d, %.1f min elapsed"
                  % (got, have, (time.time() - started) / 60.0))
            if have >= opt.target:
                bank(a)
                break
    except KeyboardInterrupt:
        print("stopped")
    finally:
        a.close()


if __name__ == "__main__":
    main()
