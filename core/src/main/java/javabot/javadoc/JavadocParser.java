package javabot.javadoc;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.inject.Injector;
import com.google.inject.persist.Transactional;
import javabot.JavabotThreadFactory;
import javabot.dao.ApiDao;
import javabot.dao.JavadocClassDao;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class JavadocParser {
  private static final Logger log = LoggerFactory.getLogger(JavadocParser.class);

  @Inject
  private ApiDao apiDao;

  @Inject
  private JavadocClassDao dao;

  @Inject
  private Injector injector;

  private final BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();

  private final ThreadPoolExecutor executor = new ThreadPoolExecutor(20, 30, 30, TimeUnit.SECONDS, workQueue,
      new JavabotThreadFactory(false, "javadoc-thread-"));

  private JavadocApi api;

  private List<String> packages;

  private final Map<String, List<JavadocClass>> deferred = new HashMap<>();

  @Transactional
  public void parse(final JavadocApi classApi, String location, final Writer writer) {
    api = classApi;
    try {
      File tmpDir = new File("/tmp");
      if (!tmpDir.exists()) {
        new File(System.getProperty("java.io.tmpdir"));
      }
      executor.prestartCoreThread();
      File file = new File(location);
      try (JarFile jarFile = new JarFile(file)) {
        for (final Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
          final JarEntry entry = entries.nextElement();
          if (entry.getName().endsWith(".class")) {
            workQueue.offer(new Runnable() {
              @Override
              public void run() {
                try {
                  JavadocClassVisitor visitor = injector.getInstance(JavadocClassVisitor.class);
                  new ClassReader(jarFile.getInputStream(entry)).accept(visitor, 0);
                } catch (Exception e) {
                  log.error(e.getMessage(), e);
                  throw new RuntimeException(e.getMessage(), e);
                }
              }
            }, 1, TimeUnit.MINUTES);
          }
        }
        while (!workQueue.isEmpty()) {
          writer.write(String.format("Waiting on %s work queue to drain.  %d items left", api.getName(),
              workQueue.size()));
          Thread.sleep(5000);
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
      } finally {
        file.delete();
        writer.write(String.format("Finished importing %s.  %s!", api.getName(),
            workQueue.isEmpty() ? "SUCCESS" : "FAILURE"));
      }
    } catch (IOException e) {
      log.debug(e.getMessage(), e);
      throw new RuntimeException(e.getMessage(), e);
    } catch (InterruptedException e) {
      log.error(e.getMessage(), e);
      throw new RuntimeException(e.getMessage());
    }
  }

  public boolean acceptPackage(final String pkg) {
    if (packages == null) {
      packages = api.getPackages() == null ? Collections.<String>emptyList()
          : Arrays.asList(api.getPackages().split(","));
    }
    if (packages.isEmpty()) {
      return true;
    }
    for (String name : packages) {
      if (pkg.startsWith(name)) {
        return true;
      }
    }
    return false;
  }

  public JavadocClass getOrQueue(final JavadocApi api, final String pkg, final String name,
      final JavadocClass newJavadocClass) {
    synchronized (deferred) {
      JavadocClass parent = null;
      for (JavadocClass javadocClass : dao.getClass(pkg, name)) {
        if (javadocClass.getApi().equals(api)) {
          parent = javadocClass;
        }
      }
      if (parent == null) {
        final String fqcn = pkg + "." + name;
        List<JavadocClass> list = deferred.get(fqcn);
        if (list == null) {
          list = new ArrayList<>();
          deferred.put(fqcn, list);
        }
        list.add(newJavadocClass);
      }
      return parent;
    }
  }

  public JavadocClass getOrCreate(final JavadocApi api, final String pkg, final String name) {
    synchronized (deferred) {
      JavadocClass cls = null;
      for (JavadocClass javadocClass : dao.getClass(pkg, name)) {
        if (javadocClass.getApi().equals(api)) {
          cls = javadocClass;
        }
      }
      if (cls == null) {
        cls = new JavadocClass(api, pkg, name);
        dao.save(cls);
      }
      final List<JavadocClass> list = deferred.get(pkg + "." + name);
      if (list != null) {
        for (JavadocClass subclass : list) {
          subclass.setSuperClass(cls.getSuperClass());
          dao.save(subclass);
        }
        deferred.remove(pkg + "." + name);
      }
      return cls;
    }
  }

  public JavadocApi getApi() {
    return api;
  }
}
