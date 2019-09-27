/**
 * @license
 * Copyright 2019 Google LLC.
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 * Code distributed by Google as part of this project is also
 * subject to an additional IP rights grant found at
 * http://polymer.github.io/PATENTS.txt
 */
import {assert} from '../../platform/chai-web.js';
import {Manifest} from '../manifest.js';
import {Runtime} from '../runtime.js';
import {StubLoader} from '../testing/stub-loader.js';
import {FakeSlotComposer} from '../testing/fake-slot-composer.js';
import {SingletonStorageProvider, CollectionStorageProvider} from '../storage/storage-provider-base.js';
import {ArcTestRunner} from '../testing/arc-test-runner.js';

function createData(value: number) {
  return {id: 'id' + value, rawData: {value}};
}

describe('FunctionalParticle', () => {
  it.only('ArcTestRunner', async () => {
    const manifest = `
schema Data
  Number value

particle Multiplier in 'a.js'
  in Data factor
  in [Data] elementsIn
  out Data sum
  out [Data] elementsOut
  out Data timesCalled

recipe R
  use 'factor' as factor
  use 'elementsIn' as elementsIn
  use 'sum' as sum
  use 'elementsOut' as elementsOut
  use 'timesCalled' as timesCalled
  Multiplier
    factor <- factor
    elementsIn <- elementsIn
    sum -> sum
    elementsOut -> elementsOut
    timesCalled -> timesCalled
`;
    const impl = `
'use strict';
defineParticle(({FunctionalParticle, log}) => {
  return class extends FunctionalParticle {
    async run({factor, elementsIn}, handles) {
      // Keep track of how many times this particle has been invoked.
      this.timesCalled = ++this.timesCalled || 1;

      const factorValue = factor == null ? 0 : factor.value;
      const valuesIn = elementsIn.map(data => data.value);
      const valuesOut = valuesIn.map(value => factorValue * value)
      let sum = 0;
      valuesOut.forEach(value => sum += value);

      const dataClass = handles.factor.entityClass;
      const convertToEntity = value => new dataClass({value});

      return {
        timesCalled: convertToEntity(this.timesCalled),
        sum: convertToEntity(sum),
        elementsOut: valuesOut.map(convertToEntity),
      };
    }
  };
});
`;

    const runner = new ArcTestRunner();
    runner.addParticleImpl('a.js', impl);
    await runner.loadManifest(manifest);
    const factor = await runner.addSingletonStore('factor', 'Data');
    const sum = await runner.addSingletonStore('sum', 'Data');
    const elementsIn = await runner.addCollectionStore('elementsIn', 'Data');
    const elementsOut = await runner.addCollectionStore('elementsOut', 'Data');
    const timesCalled = await runner.addSingletonStore('timesCalled', 'Data');
    
    console.log('before runArc');
    await runner.runArc();
    console.log('after runArc');

    assert.isNull(await timesCalled.get());
    assert.isNull(await factor.get());
    assert.isNull(await sum.get());
    assert.isEmpty(await elementsIn.toList());
    assert.isEmpty(await elementsOut.toList());

    console.log('before idle');
    await runner.idle;
    console.log('after idle');

    assert.isNull(await factor.get());
    assert.isEmpty(await elementsIn.toList());
    assert.strictEqual((await timesCalled.get()).rawData.value, 2);  // onHandleSync twice
    assert.strictEqual((await sum.get()).rawData.value, 0);
    assert.isEmpty(await elementsOut.toList());

    const checkState = async (expected: {timesCalled: number, sum: number, elementsOut: number[]}) => {
      await runner.idle;
      assert.strictEqual((await timesCalled.get()).rawData.value, expected.timesCalled);
      assert.strictEqual((await sum.get()).rawData.value, expected.sum);
      assert.sameDeepMembers((await elementsOut.toList()).map(x => x.rawData.value), expected.elementsOut);
    };

    await checkState({timesCalled: 2, sum: 0, elementsOut: []});

    await elementsIn.store(createData(1), ['key1']);
    await checkState({timesCalled: 3, sum: 0, elementsOut: [0]});

    await factor.set(createData(3));
    await checkState({timesCalled: 4, sum: 3, elementsOut: [3]});

    await elementsIn.store(createData(2), ['key2']);
    await checkState({timesCalled: 5, sum: 9, elementsOut: [3, 6]});

    await elementsIn.store(createData(3), ['key2']);
    await elementsIn.store(createData(4), ['key3']);
    await runner.idle;
    // TODO: Error here seems to be that we call onHandleUpdate twice in
    // succession, the same entities are added to the output handle each time,
    // but with different IDs. For some reason the old IDs aren't cleared out
    // when the new ones are added again.
    console.log(await elementsOut.toList());
    await checkState({timesCalled: 8, sum: 30, elementsOut: [3, 6, 9, 12]});

    await elementsIn.removeMultiple([]);
    await checkState({timesCalled: 6, sum: 0, elementsOut: []});
  });


  it('test all of it', async () => {
    const manifest = await Manifest.parse(`
schema Data
  Number value

particle P in 'a.js'
  in Data oneIn
  in [Data] manyIn
  out Data oneOut
  out [Data] manyOut

recipe R
  use 'test:oneIn' as oneIn
  use 'test:manyIn' as manyIn
  use 'test:oneOut' as oneOut
  use 'test:manyOut' as manyOut
  P
    oneIn <- oneIn
    manyIn <- manyIn
    oneOut -> oneOut
    manyOut -> manyOut
`);

    const impl = `
'use strict';
defineParticle(({FunctionalParticle, log}) => {
  async run({oneIn, manyIn}) {
    const sum = oneIn;
    manyIn.forEach(x => sum += x);
    log('Sum is: ' + sum);
    return {oneOut: sum, manyOut: [sum]};
  }
});
`;

    const runtime = new Runtime(new StubLoader({'a.js': impl}), FakeSlotComposer, manifest);
    const arc = runtime.newArc('test', 'volatile://');
    const data = arc.context.findSchemaByName('Data').entityClass();
    const stores = {
      oneIn: await arc.createStore(data.type, 'oneIn', 'test:oneIn') as SingletonStorageProvider,
      manyIn: await arc.createStore(data.type.collectionOf(), 'manyIn', 'test:manyIn') as CollectionStorageProvider,
      oneOut: await arc.createStore(data.type, 'outOut', 'test:oneOut') as SingletonStorageProvider,
      manyOut: await arc.createStore(data.type.collectionOf(), 'manyOut', 'test:manyOut') as CollectionStorageProvider,
    };

    const recipe = arc.context.recipes[0];
    for (const handle of recipe.handles) {
      handle.mapToStorage(stores[handle.localName]);
    }
    recipe.normalize();

    await arc.instantiate(recipe);

    const checkOutput = async (oneOut: number, manyOut: number[]) => {
      assert.equal(await stores.oneOut.get(), oneOut);
      assert.deepEqual(await stores.manyOut.toList(), manyOut as any);
    };

    await checkOutput(null, []);

    await stores.oneIn.set(createData(1));
    await stores.manyIn.store(createData(4), ['key']);
    await checkOutput(5, [5]);
  });
});
