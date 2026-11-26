package br.com.jotave.wannawatch.recommendation.application;

import br.com.jotave.wannawatch.recommendation.domain.TitleFeatureIndex;
import br.com.jotave.wannawatch.tmdb.importer.CatalogImportedEvent;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Guarda o índice atual. Ele é reconstruído inteiro ao subir a aplicação e ao fim de cada
 * importação; quem está no meio de uma conta continua com a referência antiga, sem trava.
 */
@Component
public class TitleFeatureIndexHolder {

    private static final Logger log = LoggerFactory.getLogger(TitleFeatureIndexHolder.class);

    private final CatalogFeatureLoader loader;
    private final RecommendationProperties props;
    private final AtomicReference<TitleFeatureIndex> current = new AtomicReference<>(TitleFeatureIndex.EMPTY);

    public TitleFeatureIndexHolder(CatalogFeatureLoader loader, RecommendationProperties props) {
        this.loader = loader;
        this.props = props;
    }

    public TitleFeatureIndex get() {
        return current.get();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        rebuild();
    }

    @EventListener(CatalogImportedEvent.class)
    public void onImport() {
        rebuild();
    }

    /** Também usado pelos testes, que montam o catálogo direto no banco. */
    public synchronized TitleFeatureIndex rebuild() {
        long start = System.nanoTime();
        CatalogFeatureLoader.CatalogData data = loader.load();
        TitleFeatureIndex index = TitleFeatureIndex.build(data.titles(), props.features(),
                props.tmdbRecommendationBonus(), props.qualityPriorVotes(), data.genreNames(), data.personNames());
        current.set(index);
        log.info("Índice de recomendação montado: {} títulos em {} ms", index.size(),
                (System.nanoTime() - start) / 1_000_000);
        return index;
    }
}
