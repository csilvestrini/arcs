import {Dictionary} from "../hot";
import {Runtime} from "../runtime";
import {StubLoader} from "./stub-loader";
import {FakeSlotComposer} from "./fake-slot-composer";
import {Manifest} from "../manifest";
import {assert} from "../../platform/assert-web";
import {Store} from "../store";
import {SingletonStorageProvider, CollectionStorageProvider, StorageProviderBase} from "../storage/storage-provider-base";
import {Arc} from "../arc";
import {EntityClass} from "../entity";
import {Schema} from "../schema";
import {Recipe} from "../recipe/recipe";
import {Type} from "../type";


export class ArcTestRunner {
  private _arc: Arc;
  private _manifest: Manifest;
  private isRunning = false;

  private readonly particleImpls: Dictionary<string> = {};
  private readonly singletonStores: Dictionary<SingletonStorageProvider> = {};
  private readonly collectionStores: Dictionary<CollectionStorageProvider> = {};

  /**
   * Adds a particle implementation file. Must be called before the manifest
   * gets loaded.
   */
  addParticleImpl(filename: string, content: string) {
    assert(!this._manifest, 'Manifest already loaded.');
    this.particleImpls[filename] = content;
    return this;
  }

  /** Parses the given manifest content and instantiates a new Arc. */
  async loadManifest(manifest: string) {
    assert(!this._manifest, 'Manifest already loaded.');
    assert(!this._arc, 'Arc already started');
    
    this._manifest = await Manifest.parse(manifest);
    const runtime = new Runtime(new StubLoader(this.particleImpls), FakeSlotComposer, this.manifest);
    this._arc = runtime.newArc('test', 'volatile://');
  }

  /**
   * Creates and returns a new Singleton store. Must be called after loading the
   * manifest but before running the arc.
   */
  async addSingletonStore(storeId: string, schemaName: string): Promise<SingletonStorageProvider> {
    const type = this.getEntityClass(schemaName).type;
    const store = await this.addStore(storeId, type) as SingletonStorageProvider;
    this.singletonStores[storeId] = store;
    return store;
  }

  /**
   * Creates and returns a new Collection store. Must be called after loading
   * the manifest but before running the arc.
   */
  async addCollectionStore(storeId: string, schemaName: string): Promise<CollectionStorageProvider> {
    const type = this.getEntityClass(schemaName).type.collectionOf();
    const store = await this.addStore(storeId, type) as CollectionStorageProvider;
    this.collectionStores[storeId] = store;
    return store;
  }

  private async addStore(storeId: string, type: Type): Promise<StorageProviderBase> {
    const store = await this.arc.createStore(type, /* name= */ null, storeId);
    for (const handle of this.recipe.handles) {
      if (handle.id === storeId) {
        handle.mapToStorage(store);
      }
    }
    return store;
  }

  /** Runs the arc. */
  async runArc() {
    assert(!this.isRunning, 'Arc already running.');

    const recipe = this.recipe;
    const options = {errors: new Map()};
    if (!recipe.normalize(options)) {
      const errors = [...options.errors.entries()].map(([k,v]) => k + ': ' + v).join('\n');
      throw new Error(`Failed to normalize recipe. Errors are:\n${errors}.`);
    }

    await this.arc.instantiate(recipe);
    this.isRunning = true;
  }

  get manifest(): Manifest {
    assert(this._manifest, 'Manifest must be loaded first.');
    return this._manifest;
  }

  get arc(): Arc {
    assert(this._manifest, 'Manifest must be loaded first.');
    assert(this._arc, 'Arc must be instantiated first.');
    return this._arc;
  }

  get recipe(): Recipe {
    assert(this.arc.context.recipes.length === 1, 'ArcTestRunner only supports manifests with one recipe.');
    return this.arc.context.recipes[0];
  }

  getSchema(schemaName: string): Schema {
    return this.manifest.findSchemaByName(schemaName);
  }

  getEntityClass(schemaName: string): EntityClass {
    return this.getSchema(schemaName).entityClass();
  }

  get idle() {
    return this.arc.idle;
  }
}
