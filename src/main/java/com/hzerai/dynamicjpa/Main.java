package com.hzerai.dynamicjpa;

import static org.hibernate.cfg.AvailableSettings.DIALECT;
import static org.hibernate.cfg.AvailableSettings.HBM2DDL_AUTO;
import static org.hibernate.cfg.AvailableSettings.SHOW_SQL;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;

import org.hibernate.dialect.H2Dialect;
import org.hibernate.jpa.HibernatePersistenceProvider;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

/**
 * 
 * @author Habib Zerai
 *
 */
public class Main {
	public static void main(String[] args)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException {
		Map<String, Class<?>> fields = new HashMap<>();
		fields.put("firstName", String.class);
		fields.put("lastName", String.class);
		fields.put("birthDate", Date.class);
		fields.put("image", byte[].class);
		fields.put("version", Long.class);
		createEntity("com.hzerai.dynamicjpa.models.Person", fields);
		DynamicEntity person = (DynamicEntity) Class.forName("com.hzerai.dynamicjpa.models.Person").getConstructor()
				.newInstance();
		person.set("firstName", "Habib");
		person.set("lastName", "Zerai");
		person.set("birthDate", new Date());
		person.set("image", new byte[] { 1, 2, 3 });
		person.set("version", 0L);

		EntityManager em = createEMF(person);
		em.getTransaction().begin();
		person = em.merge(person);
		em.flush();
		em.getTransaction().commit();
		Long id = (Long) person.get("id");

		DynamicEntity beanFromDB = (DynamicEntity) em.find(Class.forName("com.hzerai.dynamicjpa.models.Person"), id);
		System.out.println(beanFromDB.get("firstName"));
	}

	private static Class<?> createEntity(String className, Map<String, Class<?>> fields) {

		var builder = new ByteBuddy().subclass(DynamicEntity.class)
				.annotateType(AnnotationDescription.Builder.ofType(javax.persistence.Entity.class).build());
		for (Map.Entry<String, Class<?>> e : fields.entrySet()) {
			builder = builder.defineField(e.getKey(), e.getValue())
					.annotateField(AnnotationDescription.Builder.ofType(javax.persistence.Column.class).build());
		}
		builder = builder.defineField("id", Long.class).annotateField(
				AnnotationDescription.Builder.ofType(javax.persistence.Id.class).build(),
				AnnotationDescription.Builder.ofType(javax.persistence.Column.class).build(),
				AnnotationDescription.Builder.ofType(javax.persistence.GeneratedValue.class).build());
		Unloaded<?> generatedClass = builder.name(className).make();
		generatedClass.load(Main.class.getClassLoader(), ClassLoadingStrategy.Default.INJECTION);
		try {
			Class<?> cls = Class.forName(className);
			return cls;
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}

	}

	private static EntityManager createEMF(Object... entities) {
		Map<String, Object> properties = new HashMap<>();
		properties.put(DIALECT, H2Dialect.class);
		properties.put("hibernate.connection.driver_class", "org.h2.Driver");
		properties.put("hibernate.connection.username", "sa");
		properties.put("hibernate.connection.url", "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1");
		properties.put(HBM2DDL_AUTO, "create-drop");
		properties.put(SHOW_SQL, true);
		EntityManagerFactory entityManagerFactory = new HibernatePersistenceProvider()
				.createContainerEntityManagerFactory(dynamicjpa(entities), properties);
		return entityManagerFactory.createEntityManager();

	}

	private static PersistenceUnitInfo dynamicjpa(Object... entities) {
		return new PersistenceUnitInfo() {
			@Override
			public String getPersistenceUnitName() {
				return "dynamic-jpa";
			}

			@Override
			public List<String> getManagedClassNames() {
				List<String> list = new ArrayList<>();
				for (Object entity : entities) {
					list.add(entity.getClass().getName());
				}
				return list;
			}

			@Override
			public String getPersistenceProviderClassName() {
				return "org.hibernate.jpa.HibernatePersistenceProvider";
			}

			@Override
			public PersistenceUnitTransactionType getTransactionType() {
				return PersistenceUnitTransactionType.RESOURCE_LOCAL;
			}

			@Override
			public DataSource getJtaDataSource() {
				return null;
			}

			@Override
			public DataSource getNonJtaDataSource() {
				return null;
			}

			@Override
			public List<String> getMappingFileNames() {
				return Collections.emptyList();
			}

			@Override
			public List<URL> getJarFileUrls() {
				try {
					return Collections.list(this.getClass().getClassLoader().getResources(""));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}

			@Override
			public URL getPersistenceUnitRootUrl() {
				return null;
			}

			@Override
			public boolean excludeUnlistedClasses() {
				return false;
			}

			@Override
			public SharedCacheMode getSharedCacheMode() {
				return null;
			}

			@Override
			public ValidationMode getValidationMode() {
				return null;
			}

			@Override
			public Properties getProperties() {
				return new Properties();
			}

			@Override
			public String getPersistenceXMLSchemaVersion() {
				return null;
			}

			@Override
			public ClassLoader getClassLoader() {
				return null;
			}

			@Override
			public void addTransformer(ClassTransformer transformer) {

			}

			@Override
			public ClassLoader getNewTempClassLoader() {
				return null;
			}
		};
	}

	public static class DynamicEntity {

		public void set(String key, Object value) {
			try {
				java.lang.reflect.Field field = this.getClass().getDeclaredField(key);
				field.setAccessible(true);
				field.set(this, value);
				field.setAccessible(false);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public Object get(String key) {
			try {
				java.lang.reflect.Field field = this.getClass().getDeclaredField(key);
				field.setAccessible(true);
				Object value = field.get(this);
				field.setAccessible(false);
				return value;
			} catch (Exception e) {
			}
			return null;
		}

	}

}
