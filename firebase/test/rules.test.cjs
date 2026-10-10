const { test, before, after, beforeEach } = require('node:test');
const { readFileSync } = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, collection, setDoc, getDoc, getDocs, updateDoc, serverTimestamp } = require('firebase/firestore');
let env;
const token = 'a'.repeat(32);
const data = () => ({ ownerUid: 'owner', treeId: 'tree', payload: '{}', revision: 1, enabled: true, updatedAt: serverTimestamp() });
before(async () => { env = await initializeTestEnvironment({ projectId: 'demo-heritage', firestore: { rules: readFileSync('firestore.rules', 'utf8') } }); });
beforeEach(async () => { await env.clearFirestore(); });
after(async () => { await env.cleanup(); });
test('owner can read a missing capability and create its document; others cannot forge ownership', async () => {
  const owner = env.authenticatedContext('owner').firestore();
  await assertSucceeds(getDoc(doc(owner, 'sharedTrees', token)));
  await assertSucceeds(setDoc(doc(owner, 'sharedTrees', token), data()));
  const visitor = env.authenticatedContext('visitor').firestore();
  await assertFails(setDoc(doc(visitor, 'sharedTrees', 'b'.repeat(32)), data()));
});
test('authenticated capability reads work; unauthenticated reads and enumeration fail', async () => {
  const owner = env.authenticatedContext('owner').firestore();
  await setDoc(doc(owner, 'sharedTrees', token), data());
  const visitor = env.authenticatedContext('visitor').firestore();
  await assertSucceeds(getDoc(doc(visitor, 'sharedTrees', token)));
  await assertFails(getDocs(collection(visitor, 'sharedTrees')));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'sharedTrees', token)));
  await assertFails(updateDoc(doc(visitor, 'sharedTrees', token), { payload: 'changed', revision: 2, updatedAt: serverTimestamp() }));
});
test('only owner can advance revisions and revoke; revoked links deny recipients', async () => {
  const owner = env.authenticatedContext('owner').firestore(); const reference = doc(owner, 'sharedTrees', token);
  await setDoc(reference, data());
  await assertFails(updateDoc(reference, { payload: 'changed', updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(reference, { payload: 'changed', revision: 2, updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(reference, { ownerUid: 'other', revision: 3, updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(reference, { enabled: false, updatedAt: serverTimestamp() }));
  await assertFails(getDoc(doc(env.authenticatedContext('visitor').firestore(), 'sharedTrees', token)));
  await assertSucceeds(getDoc(reference));
});
test('invalid tokens, extra fields, oversized payloads, and unrelated collections fail', async () => {
  const owner = env.authenticatedContext('owner').firestore();
  await assertFails(setDoc(doc(owner, 'sharedTrees', 'guessable'), data()));
  await assertFails(setDoc(doc(owner, 'sharedTrees', token), { ...data(), extra: true }));
  await assertFails(setDoc(doc(owner, 'sharedTrees', token), { ...data(), payload: 'x'.repeat(700001) }));
  await assertFails(setDoc(doc(owner, 'privateTrees', token), data()));
});
