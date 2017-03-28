package org.snomed.heathanalytics.snomed;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.ihtsdo.otf.snomedboot.ComponentStore;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.ComponentFactoryImpl;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.ConceptImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.heathanalytics.store.ConceptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SnomedSubsumptionService {

	@Autowired
	private ConceptRepository conceptRepository;

	private Long2ObjectMap<ConceptImpl> concepts;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public Set<Long> getDescendantsOf(String conceptId) {
		return getDescendantsOf(Long.parseLong(conceptId));
	}

	public Set<Long> getDescendantsOf(Long conceptId) {
		return concepts.values().parallelStream().filter(c -> c.getAncestorIds().contains(conceptId)).map(ConceptImpl::getId).collect(Collectors.toSet());
	}

	public Set<Long> getAncestorsOf(Long conceptId) {
		return concepts.get(conceptId).getAncestorIds();
	}

	public void loadSnomedRelease(InputStream snomedReleaseZipStreamIn) throws IOException, ReleaseImportException {
		// Use 'Snomed Boot' project to unzip release and build transitive closure
		ComponentStore componentStore = new ComponentStore();
		LoadingProfile loadingProfile = LoadingProfile.light
				.withoutInactiveConcepts()
				.withoutAnyRefsets();
		new ReleaseImporter().loadSnapshotReleaseFiles(snomedReleaseZipStreamIn, loadingProfile, new ComponentFactoryImpl(componentStore));
		concepts = componentStore.getConcepts();
		// Keep all concepts in memory
	}

	private void persistConcepts() {
		logger.info("Storing {} Snomed concepts including transitive closure...", concepts.size());
		Set<Concept> batch = new HashSet<>();
		int i = 1;
		for (ConceptImpl concept : concepts.values()) {
			batch.add(new Concept(concept.getId(), concept.getFsn(), concept.getDefinitionStatusId(), concept.getAncestorIds()));
			if (i % 10000 == 0) {
				System.out.print(".");
				conceptRepository.save(batch);
				batch.clear();
			}
			if (i % 100000 == 0) {
				System.out.println();
			}
			i++;
		}
		if (!batch.isEmpty()) {
			conceptRepository.save(batch);
		}
		System.out.println();
	}

	public Long2ObjectMap<ConceptImpl> getConcepts() {
		return concepts;
	}

	public void setConcepts(Long2ObjectMap<ConceptImpl> concepts) {
		this.concepts = concepts;
	}

	public void setConcepts(Set<ConceptImpl> conceptSet) {
		concepts = new Long2ObjectOpenHashMap<>();
		for (ConceptImpl concept : conceptSet) {
			concepts.put(concept.getId(), concept);
		}
	}
}
