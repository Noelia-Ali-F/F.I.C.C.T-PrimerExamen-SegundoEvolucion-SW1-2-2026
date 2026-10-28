package com.arqnova.repositorios.dynamodb;

import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/** Bridges the existing REQUIRED application boundaries to DynamoDB TransactWriteItems. */
public final class DynamoTransactionManager extends AbstractPlatformTransactionManager {
    private final DynamoDocumentStore store;
    private static final class Transaction implements SmartTransactionObject {
        DynamoDocumentStore.Unit unit;
        Transaction(DynamoDocumentStore.Unit unit) { this.unit = unit; }
        public boolean isRollbackOnly() { return unit != null && unit.rollbackOnly; }
        public void flush() {}
    }
    public DynamoTransactionManager(DynamoDocumentStore store) { this.store = store; }
    protected Object doGetTransaction() { return new Transaction(store.unit()); }
    protected boolean isExistingTransaction(Object tx) { return ((Transaction) tx).unit != null; }
    protected void doBegin(Object tx, TransactionDefinition definition) {
        store.begin(); ((Transaction) tx).unit = store.unit();
    }
    protected void doCommit(DefaultTransactionStatus status) { store.commit(); }
    protected void doRollback(DefaultTransactionStatus status) { /* Staged mutations were never sent. */ }
    protected void doSetRollbackOnly(DefaultTransactionStatus status) { ((Transaction) status.getTransaction()).unit.rollbackOnly = true; }
    protected void doCleanupAfterCompletion(Object tx) { store.end(); }
}
