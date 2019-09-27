/**
 * @license
 * Copyright 2019 Google LLC.
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 * Code distributed by Google as part of this project is also
 * subject to an additional IP rights grant found at
 * http://polymer.github.io/PATENTS.txt
 */

import {Particle} from './particle.js';
import {Handle, Collection, Singleton} from './handle.js';
import {CollectionHandle, SingletonHandle} from './storageNG/handle.js';
import {Dictionary} from './hot.js';

// tslint:disable-next-line: no-any
export type InputValues = Dictionary<any>;
// tslint:disable-next-line: no-any
export type OutputValues = Dictionary<any>;

export abstract class FunctionalParticle extends Particle {
  private inputHandles: ReadonlyMap<string, Handle>;
  private outputHandles: ReadonlyMap<string, Handle>;
  private allHandles: Dictionary<Handle>;

  /**
   * Invoked whenever inputs change. FunctionalParticle implementations must
   * override this method as their main mode of operation. Return values for all
   * output handles.
   */
  abstract async run(inputs: InputValues, handles: Dictionary<Handle>): Promise<OutputValues>;

  protected async setHandles(handles: ReadonlyMap<string, Handle>): Promise<void> {
    const inputHandles: Map<string, Handle> = new Map();
    const outputHandles: Map<string, Handle> = new Map();
    const allHandles: Dictionary<Handle> = {};
    for (const [name, handle] of handles.entries()) {
      if (handle.canRead) {
        inputHandles.set(name, handle);
      }
      if (handle.canWrite) {
        outputHandles.set(name, handle);
      }
      allHandles[name] = handle;
    }
    this.inputHandles = inputHandles;
    this.outputHandles = outputHandles;
    this.allHandles = allHandles;
  }

  protected async onHandleSync(handle: Handle, model): Promise<void> {
    await this.runParticleFunction();
  }

  protected async onHandleUpdate(
      // tslint:disable-next-line: no-any
      handle: Handle, update: {data?: any, oldData?: any, added?: any, removed?: any, originator?: boolean}): Promise<void> {
    if (update.originator) {
      // Update came from this particle. Ignore it.
      return;
    }
    if (!handle.canRead) {
      // Update was for an output handle. Ignore it.
      return;
    }
    await this.runParticleFunction();
  }

  private async runParticleFunction() {
    // tslint:disable-next-line: no-any
    const inputs: InputValues = {};
    for (const [name, handle] of this.inputHandles) {
      if (handle instanceof Singleton || handle instanceof SingletonHandle) {
        inputs[name] = await handle.get();
      } else if (handle instanceof Collection || handle instanceof CollectionHandle) {
        inputs[name] = await handle.toList();
      }
    }

    // Run function: give all input values, receive all output values.
    // TODO: Come up with a more friendly way of creating entities than by
    // passing in handles and calling `new handle.foo.entityClass()`.
    const outputs = await this.run(inputs, this.allHandles);

    // TODO: returning null/undefined should clear all handles.

    console.log(outputs);

    // Check all required outputs are present.
    const givenOutputNames = new Set(Object.keys(outputs));
    const expectedHandleNames = new Set(this.outputHandles.keys());
    for (const expected of expectedHandleNames) {
      if (!givenOutputNames.has(expected)) {
        throw new Error(`Output for handle '${expected}' is missing.`);
      }
    }
    if (givenOutputNames.size !== expectedHandleNames.size) {
      const extras = [...givenOutputNames].filter(name => !expectedHandleNames.has(name));
      throw new Error(`Output contained unexpected handles: ${extras}.`);
    }

    // Update outputs.
    for (const [name, value] of Object.entries(outputs)) {
      console.log(`${name} = ${JSON.stringify(value)}`);
      const handle = this.outputHandles.get(name);
      if (handle instanceof Singleton || handle instanceof SingletonHandle) {
        console.log(`Setting value for singleton handle ${name}: ${value}`);
        await handle.set(value);
      } else if (handle instanceof Collection) {
        console.log(`Setting value for Collection ${name}: ${value}`);
        await handle.clear();
        for (const element of value) {
          await handle.store(element);
        }
      } else if (handle instanceof CollectionHandle) {
        console.log(`Setting value for CollectionHandle ${name}: ${value}`);
        await handle.clear();
        await handle.addMultiple(value);
      } else {
        throw new Error('Unknown handle type.');
      }
    }
  }
}
