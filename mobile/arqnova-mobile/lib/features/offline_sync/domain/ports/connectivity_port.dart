abstract interface class ConnectivityPort {
  Stream<bool> get changes;
  Future<bool> isConnected();
}
