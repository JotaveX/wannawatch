package br.com.jotave.wannawatch.tmdb.importer;

import br.com.jotave.wannawatch.catalog.domain.TitleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Gatilhos automáticos: ao subir com o banco vazio e no horário agendado. */
@Component
public class ImportTriggers {

    private static final Logger log = LoggerFactory.getLogger(ImportTriggers.class);

    private final CatalogImporter importer;
    private final ImportProperties props;
    private final TitleRepository titles;

    public ImportTriggers(CatalogImporter importer, ImportProperties props, TitleRepository titles) {
        this.importer = importer;
        this.props = props;
        this.titles = titles;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (props.onStartupIfEmpty() && titles.count() == 0) {
            log.info("Banco sem títulos: disparando a primeira importação");
            importer.startAsync("inicialização com banco vazio");
        }
    }

    @Scheduled(cron = "${wannawatch.import.cron:-}", zone = "${wannawatch.import.zone:America/Sao_Paulo}")
    public void scheduled() {
        importer.run("agendamento");
    }
}
