# Design notes on CLOCK-Pro+

## Glossary

### Entry

Instead of pages we talk about cache entries here.

### Ghost entry

Referenced as non resident cold page in the paper. We just keep the key of the entry
as history information.

## Separate clock for ghosts?

Having a separate clock for ghosts makes no difference in algorithmic behaviour.
It has performance and memory benefits to have a separate ghost list.

## Separate clock for cold and hot entries?

Having separate lists for cold and hot entries means that the entries will be
shuffled and the initial entry order gets lost. This is because when an entry
is propagated from cold to hot it gets deleted from one list and reinserted at
the tail of the other.

When propagating an entry from cold to hot, this might have positive effects since
a freshly propagated entry is far away from hand hot. When running hand hot and
moving entries back to the cold state, the reverse applies: The entry will be reinserted
in the cold list and is far away from hand cold. This has a negative effect.
However, experiments have shown that the effect can be mitigated by evicting
hot entries with no reference directly.

