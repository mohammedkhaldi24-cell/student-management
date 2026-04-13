package com.pfe.gestionetudiant.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.pfe.gestionetudiant.dto.BulletinDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;

/**
 * Service de génération de bulletins PDF avec OpenPDF (iText fork)
 */
@Service
@RequiredArgsConstructor
public class PdfService {

    // Couleurs de l'université
    private static final Color COULEUR_PRINCIPALE = new Color(31, 73, 125);   // Bleu foncé
    private static final Color COULEUR_SECONDAIRE = new Color(68, 114, 196);   // Bleu clair
    private static final Color COULEUR_VERT = new Color(70, 130, 80);
    private static final Color COULEUR_ROUGE = new Color(192, 0, 0);
    private static final Color COULEUR_ORANGE = new Color(255, 140, 0);
    private static final Color COULEUR_GRIS_CLAIR = new Color(242, 242, 242);

    public byte[] genererBulletinPdf(BulletinDto bulletin) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            Document document = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            // ======================================================
            // EN-TÊTE
            // ======================================================
            ajouterEnTete(document, bulletin);

            // ======================================================
            // INFORMATIONS ÉTUDIANT
            // ======================================================
            ajouterInfoEtudiant(document, bulletin);

            document.add(Chunk.NEWLINE);

            // ======================================================
            // TABLEAU DES NOTES
            // ======================================================
            ajouterTableauNotes(document, bulletin);

            document.add(Chunk.NEWLINE);

            // ======================================================
            // RÉCAPITULATIF
            // ======================================================
            ajouterRecapitulatif(document, bulletin);

            document.add(Chunk.NEWLINE);

            // ======================================================
            // ABSENCES
            // ======================================================
            ajouterAbsences(document, bulletin);

            document.add(Chunk.NEWLINE);

            // ======================================================
            // SIGNATURES
            // ======================================================
            ajouterSignatures(document, bulletin);

            // ======================================================
            // FILIGRANE
            // ======================================================
            ajouterFiligrane(writer, bulletin);

            document.close();

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération du PDF : " + e.getMessage(), e);
        }

        return baos.toByteArray();
    }

    private void ajouterEnTete(Document document, BulletinDto bulletin) throws DocumentException {
        // Tableau en-tête à 3 colonnes
        PdfPTable enTete = new PdfPTable(3);
        enTete.setWidthPercentage(100);
        enTete.setWidths(new float[]{2f, 4f, 2f});
        enTete.setSpacingAfter(10f);

        // Logo / République
        PdfPCell cellGauche = new PdfPCell();
        cellGauche.setBorder(Rectangle.NO_BORDER);
        Font fontRepublique = FontFactory.getFont(FontFactory.HELVETICA, 7, Font.NORMAL, COULEUR_PRINCIPALE);
        cellGauche.addElement(new Paragraph("République Algérienne\nDémocratique et Populaire\n\n" +
                "Ministère de l'Enseignement\nSupérieur et de la\nRecherche Scientifique", fontRepublique));
        enTete.addCell(cellGauche);

        // Titre central
        PdfPCell cellCentre = new PdfPCell();
        cellCentre.setBorder(Rectangle.NO_BORDER);
        cellCentre.setHorizontalAlignment(Element.ALIGN_CENTER);
        Font fontUniv = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD, COULEUR_PRINCIPALE);
        Font fontTitre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.BOLD, COULEUR_PRINCIPALE);

        Paragraph pUniv = new Paragraph("UNIVERSITÉ - FACULTÉ DES SCIENCES", fontUniv);
        pUniv.setAlignment(Element.ALIGN_CENTER);
        cellCentre.addElement(pUniv);
        cellCentre.addElement(new Paragraph(" "));

        Paragraph pBulletin = new Paragraph("BULLETIN DE NOTES", fontTitre);
        pBulletin.setAlignment(Element.ALIGN_CENTER);
        cellCentre.addElement(pBulletin);

        Font fontSem = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD, COULEUR_SECONDAIRE);
        Paragraph pSem = new Paragraph(bulletin.getSemestre() + " — " + bulletin.getAnneeAcademique(), fontSem);
        pSem.setAlignment(Element.ALIGN_CENTER);
        cellCentre.addElement(pSem);
        enTete.addCell(cellCentre);

        // Droit (date de génération)
        PdfPCell cellDroite = new PdfPCell();
        cellDroite.setBorder(Rectangle.NO_BORDER);
        cellDroite.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Font fontDate = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.ITALIC, Color.GRAY);
        cellDroite.addElement(new Paragraph("Généré le :\n" +
                java.time.LocalDate.now().toString(), fontDate));
        enTete.addCell(cellDroite);

        document.add(enTete);

        // Ligne séparatrice
        PdfPTable ligneSep = new PdfPTable(1);
        ligneSep.setWidthPercentage(100);
        PdfPCell ligne = new PdfPCell(new Phrase(""));
        ligne.setBackgroundColor(COULEUR_PRINCIPALE);
        ligne.setFixedHeight(3f);
        ligne.setBorder(Rectangle.NO_BORDER);
        ligneSep.addCell(ligne);
        document.add(ligneSep);
    }

    private void ajouterInfoEtudiant(Document document, BulletinDto bulletin) throws DocumentException {
        document.add(new Paragraph(" "));

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1f, 1f});

        Font fontLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, COULEUR_PRINCIPALE);
        Font fontValue = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.BLACK);

        ajouterLigneInfo(infoTable, "Nom et Prénom :",
                bulletin.getNomEtudiant() + " " + bulletin.getPrenomEtudiant(),
                fontLabel, fontValue);
        ajouterLigneInfo(infoTable, "Matricule :", bulletin.getMatricule(), fontLabel, fontValue);
        ajouterLigneInfo(infoTable, "Filière :", bulletin.getFiliere(), fontLabel, fontValue);
        ajouterLigneInfo(infoTable, "Classe :", bulletin.getNomClasse(), fontLabel, fontValue);

        document.add(infoTable);
    }

    private void ajouterLigneInfo(PdfPTable table, String label, String value,
                                   Font fontLabel, Font fontValue) {
        PdfPCell cellLabel = new PdfPCell(new Phrase(label, fontLabel));
        cellLabel.setBorder(Rectangle.NO_BORDER);
        cellLabel.setPaddingBottom(5f);
        table.addCell(cellLabel);

        PdfPCell cellValue = new PdfPCell(new Phrase(value != null ? value : "-", fontValue));
        cellValue.setBorder(Rectangle.NO_BORDER);
        cellValue.setPaddingBottom(5f);
        table.addCell(cellValue);
    }

    private void ajouterTableauNotes(Document document, BulletinDto bulletin) throws DocumentException {
        Font fontEntete = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, Color.WHITE);
        Font fontCell = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.BLACK);
        Font fontCellBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, Color.BLACK);

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4f, 1f, 1.2f, 1.5f, 1.5f, 1.8f, 1.5f});
        table.setSpacingBefore(5f);

        // En-tête du tableau
        String[] headers = {"Module", "Coeff.", "CC (40%)", "Examen (60%)", "Note / 20", "Note × Coeff.", "Résultat"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, fontEntete));
            cell.setBackgroundColor(COULEUR_PRINCIPALE);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6f);
            cell.setBorderColor(Color.WHITE);
            table.addCell(cell);
        }

        boolean alternatif = false;
        if (bulletin.getNotes() != null) {
            for (BulletinDto.LigneNoteDto ligne : bulletin.getNotes()) {
                Color bg = alternatif ? COULEUR_GRIS_CLAIR : Color.WHITE;

                addTableCell(table, ligne.getModuleNom(), fontCellBold, bg, Element.ALIGN_LEFT);
                addTableCell(table, String.valueOf(ligne.getCoefficient()), fontCell, bg, Element.ALIGN_CENTER);
                addTableCell(table, formatNote(ligne.getNoteCC()), fontCell, bg, Element.ALIGN_CENTER);
                addTableCell(table, formatNote(ligne.getNoteExamen()), fontCell, bg, Element.ALIGN_CENTER);

                // Note finale colorée
                Color noteCouleur = getNoteColor(ligne.getNoteFinal());
                Font fontNote = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, noteCouleur);
                addTableCell(table, formatNote(ligne.getNoteFinal()), fontNote, bg, Element.ALIGN_CENTER);

                addTableCell(table, formatNote(ligne.getNoteXCoeff()), fontCell, bg, Element.ALIGN_CENTER);

                // Résultat coloré
                Color statutColor = getStatutColor(ligne.getStatut());
                Font fontStatut = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.BOLD, statutColor);
                addTableCell(table, ligne.getStatut() != null ? ligne.getStatut() : "-", fontStatut, bg, Element.ALIGN_CENTER);

                alternatif = !alternatif;
            }
        }

        document.add(table);
    }

    private void ajouterRecapitulatif(Document document, BulletinDto bulletin) throws DocumentException {
        PdfPTable recap = new PdfPTable(4);
        recap.setWidthPercentage(60);
        recap.setHorizontalAlignment(Element.ALIGN_RIGHT);
        recap.setWidths(new float[]{2.5f, 1.5f, 2f, 2f});

        Font fontLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.WHITE);
        Font fontValue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD, Color.WHITE);

        // Labels
        addColoredCell(recap, "Moyenne Générale", COULEUR_PRINCIPALE, fontLabel);
        addColoredCell(recap, "Rang", COULEUR_PRINCIPALE, fontLabel);
        addColoredCell(recap, "Mention", COULEUR_PRINCIPALE, fontLabel);
        addColoredCell(recap, "Décision", COULEUR_PRINCIPALE, fontLabel);

        // Valeurs
        Color moyenneColor = bulletin.getMoyenneGenerale() >= 10 ? COULEUR_VERT : COULEUR_ROUGE;
        Font fontMoy = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.BOLD, Color.WHITE);
        addColoredCell(recap, String.format("%.2f / 20", bulletin.getMoyenneGenerale()), moyenneColor, fontMoy);
        addColoredCell(recap, bulletin.getRangClasse() + "/" + bulletin.getTotalEtudiants(), COULEUR_SECONDAIRE, fontValue);
        addColoredCell(recap, bulletin.getMention(), COULEUR_SECONDAIRE, fontValue);

        Color decisionColor = bulletin.getMoyenneGenerale() >= 10 ? COULEUR_VERT :
                (bulletin.getMoyenneGenerale() >= 7 ? COULEUR_ORANGE : COULEUR_ROUGE);
        addColoredCell(recap, bulletin.getDecision(), decisionColor, fontValue);

        document.add(recap);
    }

    private void ajouterAbsences(Document document, BulletinDto bulletin) throws DocumentException {
        Font fontTitre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, COULEUR_PRINCIPALE);
        Font fontInfo = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.BLACK);

        Paragraph pAbsTitle = new Paragraph("Récapitulatif des Absences", fontTitre);
        document.add(pAbsTitle);

        PdfPTable absTable = new PdfPTable(3);
        absTable.setWidthPercentage(70);
        absTable.setWidths(new float[]{3f, 1.5f, 1.5f});
        absTable.setSpacingBefore(5f);

        Font fontEnt = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, Color.WHITE);
        addColoredCell(absTable, "Type", COULEUR_SECONDAIRE, fontEnt);
        addColoredCell(absTable, "Heures", COULEUR_SECONDAIRE, fontEnt);
        addColoredCell(absTable, "Séances (3h)", COULEUR_SECONDAIRE, fontEnt);

        int totalH = bulletin.getTotalHeuresAbsence();
        int njH = bulletin.getTotalHeuresAbsenceNonJustifiee();
        int jH = totalH - njH;

        Font fontRow = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.BLACK);
        addTableCell(absTable, "Total absences", fontRow, Color.WHITE, Element.ALIGN_LEFT);
        addTableCell(absTable, String.valueOf(totalH) + "h", fontRow, Color.WHITE, Element.ALIGN_CENTER);
        addTableCell(absTable, String.valueOf(totalH / 3), fontRow, Color.WHITE, Element.ALIGN_CENTER);

        addTableCell(absTable, "Justifiées", fontRow, COULEUR_GRIS_CLAIR, Element.ALIGN_LEFT);
        Font fontJustifiee = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, COULEUR_VERT);
        addTableCell(absTable, String.valueOf(jH) + "h", fontJustifiee, COULEUR_GRIS_CLAIR, Element.ALIGN_CENTER);
        addTableCell(absTable, String.valueOf(jH / 3), fontJustifiee, COULEUR_GRIS_CLAIR, Element.ALIGN_CENTER);

        addTableCell(absTable, "Non Justifiées", fontRow, Color.WHITE, Element.ALIGN_LEFT);
        Font fontNJ = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, COULEUR_ROUGE);
        addTableCell(absTable, String.valueOf(njH) + "h", fontNJ, Color.WHITE, Element.ALIGN_CENTER);
        addTableCell(absTable, String.valueOf(njH / 3), fontNJ, Color.WHITE, Element.ALIGN_CENTER);

        document.add(absTable);
    }

    private void ajouterSignatures(Document document, BulletinDto bulletin) throws DocumentException {
        document.add(Chunk.NEWLINE);
        Font fontSig = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, Color.BLACK);
        Font fontSigLabel = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.ITALIC, Color.GRAY);

        PdfPTable sigTable = new PdfPTable(3);
        sigTable.setWidthPercentage(100);

        PdfPCell sig1 = new PdfPCell();
        sig1.setBorder(Rectangle.TOP);
        sig1.setBorderColor(COULEUR_PRINCIPALE);
        sig1.setPaddingTop(10f);
        sig1.addElement(new Paragraph("Chef de Filière", fontSig));
        sig1.addElement(new Paragraph("Cachet et Signature", fontSigLabel));
        sigTable.addCell(sig1);

        PdfPCell sig2 = new PdfPCell();
        sig2.setBorder(Rectangle.TOP);
        sig2.setBorderColor(COULEUR_PRINCIPALE);
        sig2.setPaddingTop(10f);
        sig2.setHorizontalAlignment(Element.ALIGN_CENTER);
        sig2.addElement(new Paragraph("Doyen de la Faculté", fontSig));
        sig2.addElement(new Paragraph("Cachet et Signature", fontSigLabel));
        sigTable.addCell(sig2);

        PdfPCell sig3 = new PdfPCell();
        sig3.setBorder(Rectangle.TOP);
        sig3.setBorderColor(COULEUR_PRINCIPALE);
        sig3.setPaddingTop(10f);
        sig3.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sig3.addElement(new Paragraph("L'Étudiant(e)", fontSig));
        sig3.addElement(new Paragraph("Signature", fontSigLabel));
        sigTable.addCell(sig3);

        document.add(sigTable);
    }

    private void ajouterFiligrane(PdfWriter writer, BulletinDto bulletin) {
        try {
            PdfContentByte canvas = writer.getDirectContentUnder();
            Font fontFil = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 60, Font.BOLD,
                    new Color(200, 200, 200, 50));
            PdfGState gs = new PdfGState();
            gs.setFillOpacity(0.15f);
            canvas.setGState(gs);

            ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
                    new Phrase(bulletin.getDecision(), fontFil),
                    297f, 420f, 45f);
        } catch (Exception ignored) { /* filigrane non critique */ }
    }

    // ── Méthodes utilitaires ──────────────────────────────────────

    private void addTableCell(PdfPTable table, String text, Font font,
                               Color bg, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5f);
        cell.setBorderColor(new Color(210, 210, 210));
        table.addCell(cell);
    }

    private void addColoredCell(PdfPTable table, String text, Color bg, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(7f);
        cell.setBorderColor(Color.WHITE);
        table.addCell(cell);
    }

    private String formatNote(Double note) {
        if (note == null) return "-";
        return String.format("%.2f", note);
    }

    private Color getNoteColor(Double note) {
        if (note == null) return Color.GRAY;
        if (note >= 10) return COULEUR_VERT;
        if (note >= 7) return COULEUR_ORANGE;
        return COULEUR_ROUGE;
    }

    private Color getStatutColor(String statut) {
        if (statut == null) return Color.GRAY;
        return switch (statut) {
            case "Admis" -> COULEUR_VERT;
            case "Rattrapage" -> COULEUR_ORANGE;
            case "Ajourné" -> COULEUR_ROUGE;
            default -> Color.GRAY;
        };
    }
}
