#!/usr/bin/env python3
"""Command line for the Agent script's socket.

The Agent script (scripts/APOS/Agent.java) turns a running client into
something you can drive one line at a time. This is the other end of that
socket, and it exists so you do not have to remember the wire format:

    tools/agent.py state
    tools/agent.py scene 15
    tools/agent.py walkTo 330 557
    tools/agent.py attackNpc 3

Any public method on Script is a valid command -- the client dispatches by
name, so this tool needs no table of verbs and never goes out of date.

Pass several commands separated by -- to run them over one connection, which
matters when you are stepping something through quickly:

    tools/agent.py walkTo 330 557 -- state

--watch repeats a command until it prints something new, for waiting on a
walk or a fight to finish without a sleep-and-hope loop.
"""

import argparse
import json
import socket
import sys
import time

DEFAULT_PORT = 43800


class Agent(object):
    def __init__(self, host="127.0.0.1", port=DEFAULT_PORT, timeout=20.0):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.rf = self.sock.makefile("r")
        self.n = 0

    def call(self, method, *args):
        self.n += 1
        parts = [str(self.n), method]
        for a in args:
            a = str(a)
            parts.append('"%s"' % a if " " in a else a)
        self.sock.sendall((" ".join(parts) + "\n").encode())
        line = self.rf.readline()
        if not line:
            raise IOError("client closed the connection")
        return json.loads(line)

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


def show(reply, raw):
    if raw:
        print(json.dumps(reply))
    elif reply.get("ok"):
        print(json.dumps(reply.get("result")))
    else:
        print("error: %s" % reply.get("error"), file=sys.stderr)
    return bool(reply.get("ok"))


def main():
    p = argparse.ArgumentParser(add_help=True)
    p.add_argument("--port", type=int, default=DEFAULT_PORT)
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--raw", action="store_true", help="print the whole reply envelope")
    p.add_argument("--watch", type=float, metavar="SECS",
                   help="repeat until the result changes or SECS elapse")
    p.add_argument("command", nargs=argparse.REMAINDER)
    opt = p.parse_args()

    if not opt.command:
        p.error("give a command, e.g. state")

    # Split on -- into separate calls sharing one connection.
    batches, cur = [], []
    for tok in opt.command:
        if tok == "--":
            if cur:
                batches.append(cur)
            cur = []
        else:
            cur.append(tok)
    if cur:
        batches.append(cur)

    try:
        a = Agent(opt.host, opt.port)
    except OSError as e:
        print("cannot reach the agent on %s:%d (%s) -- is the client running "
              "with the Agent script loaded?" % (opt.host, opt.port, e), file=sys.stderr)
        return 2

    ok = True
    try:
        if opt.watch is not None:
            first = a.call(*batches[0])
            baseline = json.dumps(first.get("result"))
            deadline = time.time() + opt.watch
            while time.time() < deadline:
                time.sleep(0.4)
                reply = a.call(*batches[0])
                if json.dumps(reply.get("result")) != baseline:
                    return 0 if show(reply, opt.raw) else 1
            ok = show(first, opt.raw)
        else:
            for b in batches:
                ok = show(a.call(*b), opt.raw) and ok
    finally:
        a.close()
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
