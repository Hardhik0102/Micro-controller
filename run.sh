#!/usr/bin/env bash
# Usage: ./run.sh [multi|standalone|demo|test|bench]
cd "$(dirname "$0")"
[ -d out ] || ./build.sh
case "${1:-multi}" in
  multi)      java -cp out com.team.ms51sim.Main ;;                 # UI + Core + Logger (3 processes)
  standalone) java -cp out com.team.ms51sim.Main --standalone ;;    # Week 3 single process
  demo)       java -cp out com.team.ms51sim.Main --demo-ipc ;;      # headless 3-process demo
  test)       java -cp out com.team.ms51sim.tests.IpcTests docs/week-04/test-results-table.md ;;
  bench)      java -cp out com.team.ms51sim.Main --bench docs/week-04/benchmark-raw.md ;;
  *) echo "usage: $0 [multi|standalone|demo|test|bench]"; exit 2 ;;
esac
