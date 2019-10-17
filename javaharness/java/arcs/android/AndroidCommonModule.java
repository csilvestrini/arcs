package arcs.android;

import arcs.api.PortableJsonParser;
import dagger.Binds;
import dagger.Module;

/** Dagger module for classes common to all Android modules. */
@Module
public abstract class AndroidCommonModule {

  @Binds
  abstract PortableJsonParser providesPortableJsonParser(AndroidPortableJsonParser impl);
}