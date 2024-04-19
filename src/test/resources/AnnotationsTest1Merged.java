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
<<<<<<< HEAD
      public @PolyNull @PolySigned Object[] toArray() {
||||||| 8ff79e15e
      public Object[] toArray() {
        return standardToArray();
=======
      public Object[] toArray() {
>>>>>>> 0a17f4a429323589396c38d8ce75ca058faa6c64
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
<<<<<<< HEAD
      public <T extends @Nullable @UnknownSignedness Object> T[] toArray(T[] array) {
||||||| 8ff79e15e
      public <T> T[] toArray(T[] array) {
=======
      public <T extends @Nullable Object> T[] toArray(T[] array) {
>>>>>>> 0a17f4a429323589396c38d8ce75ca058faa6c64
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
