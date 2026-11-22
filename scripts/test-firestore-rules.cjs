const { before, after, beforeEach, test } = require('node:test');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, collection, getDocs } = require('firebase/firestore');

let environment;
before(async () => {
  environment = await initializeTestEnvironment({
    projectId: 'demo-spotifly-rules',
    firestore: {
      host: '127.0.0.1', port: 8080,
      rules: readFileSync(resolve(__dirname, '../firestore.rules'), 'utf8')
    }
  });
});
beforeEach(async () => environment.clearFirestore());
after(async () => { if (environment) await environment.cleanup(); });

test('signed-out clients cannot read or write library records', async () => {
  const database = environment.unauthenticatedContext().firestore();
  const reference = doc(database, 'users/alice/library/manifest');
  await assertFails(getDoc(reference));
  await assertFails(setDoc(reference, { revision: 'one' }));
});

test('an owner can read and write their manifest and revision chunks', async () => {
  const database = environment.authenticatedContext('alice').firestore();
  const manifest = doc(database, 'users/alice/library/manifest');
  const chunk = doc(database, 'users/alice/revisions/one/chunks/zero');
  await assertSucceeds(setDoc(manifest, { revision: 'one' }));
  await assertSucceeds(setDoc(chunk, { data: 'playlist' }));
  await assertSucceeds(getDoc(manifest));
  await assertSucceeds(getDocs(collection(database, 'users/alice/revisions/one/chunks')));
});

test('another signed-in account cannot read, list, or overwrite owner data', async () => {
  const database = environment.authenticatedContext('bob').firestore();
  const reference = doc(database, 'users/alice/library/manifest');
  await assertFails(getDoc(reference));
  await assertFails(setDoc(reference, { revision: 'malicious' }));
  await assertFails(getDocs(collection(database, 'users/alice/library')));
});

test('authenticated clients cannot store data outside their user namespace', async () => {
  const database = environment.authenticatedContext('alice').firestore();
  await assertFails(setDoc(doc(database, 'public/library'), { data: 'wrong path' }));
  await assertFails(getDoc(doc(database, 'public/library')));
});
