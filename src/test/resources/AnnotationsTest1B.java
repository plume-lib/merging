private static final class UnmodifiableEntry<K, V> extends ForwardingMapEntry<K, V> {

  private final Entry<K, V> delegate;

  static <K, V> Set<Entry<K, V>> transformEntries(Set<Entry<K, V>> entries) {
    return new ForwardingSet<Map.Entry<K, V>>() {
      @Override
      protected Set<Entry<K, V>> delegate() {
        return entries;
      }

      @Override
      public Iterator<Entry<K, V>> iterator() {
        return UnmodifiableEntry.transformEntries(super.iterator());
      }

      @Override
      public Object[] toArray() {
        /*
         * standardToArray returns `@Nullable Object[]` rather than `Object[]` but only because it
         * can be used with collections that may contain null. This collection is a collection of
         * non-null Entry objects (Entry objects that might contain null values but are not
         * themselves null), so we can treat it as a plain `Object[]`.
         */
        @SuppressWarnings("nullness")
        Object[] result = standardToArray();
        return result;
      }

      @Override
      @SuppressWarnings("nullness") // b/192354773 in our checker affects toArray declarations
      public <T extends @Nullable Object> T[] toArray(T[] array) {
        return standardToArray(array);
      }
    };
  }

  private static <K, V> Iterator<Entry<K, V>> transformEntries(Iterator<Entry<K, V>> entries) {
    return Iterators.transform(entries, UnmodifiableEntry::new);
  }

  private UnmodifiableEntry(java.util.Map.Entry<K, V> delegate) {
    this.delegate = checkNotNull(delegate);
  }

  @Override
  protected Entry<K, V> delegate() {
    return delegate;
  }

  @Override
  public V setValue(V value) {
    throw new UnsupportedOperationException();
  }
}
