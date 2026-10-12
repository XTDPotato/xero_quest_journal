package xtdpotato.xero_quest_journal.forgecompat;

public interface StreamCodec<B, V> {
    V decode(B buffer);

    void encode(B buffer, V value);

    static <B, V> StreamCodec<B, V> unit(V value) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buffer) {
                return value;
            }

            @Override
            public void encode(B buffer, V ignored) {
            }
        };
    }
}
