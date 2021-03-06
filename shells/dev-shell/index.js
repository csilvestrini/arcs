/**
 * @license
 * Copyright 2020 Google LLC.
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 * Code distributed by Google as part of this project is also
 * subject to an additional IP rights grant found at
 * http://polymer.github.io/PATENTS.txt
 */

import './file-pane.js';
import './output-pane.js';
import '../configuration/whitelisted.js';
import '../lib/platform/loglevel-web.js';

import {Arc} from '../../build/runtime/arc.js';
import {Runtime} from '../../build/runtime/runtime.js';
import {RecipeResolver} from '../../build/runtime/recipe-resolver.js';
import {devtoolsArcInspectorFactory} from '../../build/devtools-connector/devtools-arc-inspector.js';
import {SlotObserver} from '../lib/xen-renderer.js';

// how to reach arcs root from our URL/CWD
const root = '../..';

// extra params for created arcs
const extraArcParams = {
  inspectorFactory: devtoolsArcInspectorFactory
};

// import DOM node references
const {
  filePane,
  outputPane,
  popupContainer,
  executeButton,
  toggleFilesButton,
  exportFilesButton,
  helpButton
} = window;

init();

function init() {
  // prepare ui
  filePane.init(execute, toggleFilesButton, exportFilesButton);
  executeButton.addEventListener('click', execute);
  helpButton.addEventListener('click', showHelp);
  popupContainer.addEventListener('click', () => popupContainer.style.display = 'none');
  // scan window parameters
  const params = new URLSearchParams(window.location.search);
  // set logLevel
  window.logLevel = (params.get('log') !== null) ? 1 : 0;
  // seed manifest as requested
  const manifestParam = params.get('m') || params.get('manifest');
  if (manifestParam) {
    filePane.seedManifest(manifestParam.split(';').map(m => `import '${m}'`));
    execute();
  } else {
    const exampleManifest = `\
import 'https://$particles/Tutorial/Javascript/1_HelloWorld/HelloWorld.arcs'

store DataStore of Data {num: Number, txt: Text} with {
  {num: 73, txt: 'abc'}
}

particle P in 'a.js'
  root: consumes Slot
  data: reads Data {num: Number, txt: Text}

recipe
  h0: copy DataStore
  P
    data: reads h0`;
    const exampleParticle = `\
defineParticle(({SimpleParticle, html, log}) => {
  return class extends SimpleParticle {
    get template() {
      log(\`Add '?log' to the URL to enable particle logging\`);
      return \`<div style="padding: 8px;"><span>{{num}}</span> : <span>{{str}}</span></div>\`;
    }
    render({data}) {
      return data ? {num: data.num, str: data.txt} : {};
    }
  };
});`;
    filePane.seedExample(exampleManifest, exampleParticle);
  }
}

function execute() {
  wrappedExecute().catch(e => {
    outputPane.showError('Unhandled exception', e.stack);
    console.error(e);
  });
}

async function wrappedExecute() {
  // clear ui
  document.dispatchEvent(new Event('clear-arcs-explorer'));
  outputPane.reset();
  // establish a runtime using custom parameters
  const runtime = new Runtime({rootPath: root, staticMap: filePane.getFileMap()});
  runtime.loader.flushCaches();
  // attempt to parse the context manifest
  try {
    runtime.context = await runtime.parse(filePane.getManifest(), {fileName: './manifest', throwImportErrors: true});
  } catch (e) {
    outputPane.showError('Error in Manifest.parse', e);
    return;
  }
  // check for existence of recipes
  if (runtime.context.allRecipes.length == 0) {
    outputPane.showError('No recipes found in Manifest.parse');
  }
  // instantiate an arc for each recipe in context
  let arcIndex = 1;
  for (const recipe of runtime.context.allRecipes) {
    createRecipeArc(recipe, runtime, arcIndex++);
  }
}

async function createRecipeArc(recipe, runtime, index) {
  // ask runtime to assemble arc parameter boilerplate (argument is the arc name)
  const params = runtime.buildArcParams(`arc${index}`);
  // construct the arc
  const arc = new Arc({...params, extraArcParams});
  // establish a UI Surface
  const arcPanel = outputPane.addArcPanel(params.id);
  // attach a renderer (SlotObserver and a DOM node) to the composer
  params.slotComposer.observeSlots(new SlotObserver(arcPanel.shadowRoot));
  // attach arc to bespoke shell ui
  arcPanel.attachArc(arc);
  arc.arcPanel = arcPanel;
  try {
    normalizeRecipe(arc, recipe);
    const resolvedRecipe = await resolveRecipe(arc, recipe);
    await instantiateRecipe(arc, resolvedRecipe);
  } catch (x) {
    arcPanel.showError('recipe error', x);
    return;
  }
  // display description
  await arcPanel.arcInstantiated(await runtime.getArcDescription(arc));
}

function normalizeRecipe(arc, recipe) {
  const errors = new Map();
  if (!recipe.normalize({errors})) {
    throw `Error in recipe.normalize: ${[...errors.values()].join('\n')}`;
  }
}

async function resolveRecipe(arc, recipe) {
  let resolved = recipe;
  if (!recipe.isResolved()) {
    const errors = new Map();
    const resolver = new RecipeResolver(arc);
    resolved = await resolver.resolve(recipe, {errors});
    if (!resolved) {
      throw `Error in RecipeResolver: ${[...errors.entries()].join('\n')}.\n${recipe.toString()}`;
    }
  }
  return resolved;
}

async function instantiateRecipe(arc, recipe) {
  try {
    await arc.instantiate(recipe);
  } catch (e) {
    throw `Error in arc.instantiate: ${e}`;
  }
}

function showHelp() {
  popupContainer.style.display = 'block';
  document.addEventListener('keydown', hideHelp);
}

function hideHelp(e) {
  popupContainer.style.display = 'none';
  e.preventDefault();
  document.removeEventListener('keydown', hideHelp);
}
