import 'package:dz_eid/dz_eid.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Résultat : photo, signature, identité bilingue, contrôles de sécurité et JSON.
class ResultScreen extends StatelessWidget {
  const ResultScreen({super.key, required this.record, required this.duration});

  final IdentityRecord record;
  final Duration duration;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final h = record.holder;
    final d = record.document;
    final v = record.verification;
    final pa = v.passiveAuthentication;
    final seconds = (duration.inMilliseconds / 1000).toStringAsFixed(1).replaceAll('.', ',');

    return Scaffold(
      appBar: AppBar(title: const Text('Carte lue')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            _Picture(image: record.photo, width: 110, height: 140, placeholder: Icons.person),
            const SizedBox(width: 16),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(h.lastNameLatin ?? '', style: theme.textTheme.titleLarge),
                Text(h.firstNameLatin ?? '', style: theme.textTheme.titleMedium),
                const SizedBox(height: 6),
                _Arabic(h.lastNameArabic, style: theme.textTheme.titleLarge),
                _Arabic(h.firstNameArabic, style: theme.textTheme.titleMedium),
                const SizedBox(height: 6),
                Text('Lu en $seconds s (${v.accessMethod ?? '?'})', style: theme.textTheme.bodySmall),
              ]),
            ),
          ]),
          const SizedBox(height: 16),
          Wrap(spacing: 8, runSpacing: 8, children: [
            _Badge('Intégrité', pa.dataIntegrity),
            _Badge('Signature', pa.signature),
            _Badge('CSCA', pa.certificateChain),
            _Badge('Anti-clonage', v.activeAuthentication.result),
          ]),
          if (pa.summary != null) ...[
            const SizedBox(height: 8),
            Text(pa.summary!, style: theme.textTheme.bodySmall),
          ],
          if (v.activeAuthentication.summary != null)
            Text(v.activeAuthentication.summary!, style: theme.textTheme.bodySmall),
          const SizedBox(height: 8),
          _Section('Titulaire', [
            _Field('NIN', h.nin),
            _Field('Sexe', h.sex, arabic: h.sexArabic),
            _Field('Date de naissance', h.dateOfBirth),
            _Field('Lieu de naissance', h.placeOfBirthLatin, arabic: h.placeOfBirthArabic),
            _Field('Nationalité', h.nationality),
            _Field('Groupe sanguin', h.bloodGroup),
            _Field('Adresse', h.address),
          ]),
          _Section('Document', [
            _Field('Numéro', d.number),
            _Field('Délivrée le', d.dateOfIssue),
            _Field('Expire le', d.dateOfExpiry),
            _Field('Autorité', d.issuingAuthorityLatin, arabic: d.issuingAuthorityArabic),
            _Field('État émetteur', d.issuingState),
          ]),
          if (record.signatureImage != null)
            _Section('Signature du titulaire', [
              Padding(
                padding: const EdgeInsets.all(8),
                child: Container(
                  color: Colors.white,
                  padding: const EdgeInsets.all(8),
                  child: _Picture(image: record.signatureImage, width: double.infinity, height: 90, placeholder: Icons.draw),
                ),
              ),
            ]),
          if (record.warnings.isNotEmpty)
            _Section('Avertissements', [for (final w in record.warnings) ListTile(dense: true, title: Text(w))]),
          Card(
            child: ExpansionTile(
              title: const Text('JSON (contrat IdentityRecord)'),
              childrenPadding: const EdgeInsets.all(12),
              children: [
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton.icon(
                    icon: const Icon(Icons.copy, size: 18),
                    label: const Text('Copier'),
                    onPressed: () {
                      Clipboard.setData(ClipboardData(text: record.toPrettyJson()));
                      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('JSON copié.')));
                    },
                  ),
                ),
                SelectableText(_jsonWithoutImages(), style: const TextStyle(fontFamily: 'monospace', fontSize: 11)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// JSON affiché sans les images base64 (trop longues à l'écran).
  String _jsonWithoutImages() {
    var text = record.toPrettyJson();
    for (final img in [record.photo, record.signatureImage]) {
      if (img != null && img.base64.isNotEmpty) {
        text = text.replaceAll(img.base64, '… ${img.bytes.length} octets …');
      }
    }
    return text;
  }
}

class _Picture extends StatelessWidget {
  const _Picture({required this.image, required this.width, required this.height, required this.placeholder});

  final EidImage? image;
  final double width;
  final double height;
  final IconData placeholder;

  @override
  Widget build(BuildContext context) {
    final img = image;
    final Widget child;
    if (img != null && img.isDisplayable) {
      child = Image.memory(img.bytes, fit: BoxFit.contain, gaplessPlayback: true);
    } else {
      child = Column(mainAxisAlignment: MainAxisAlignment.center, children: [
        Icon(placeholder, size: 40, color: Colors.grey),
        if (img != null) Text(img.mimeType, style: const TextStyle(fontSize: 10, color: Colors.grey)),
      ]);
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: Container(width: width, height: height, color: Colors.grey.shade200, child: child),
    );
  }
}

class _Badge extends StatelessWidget {
  const _Badge(this.label, this.status);

  final String label;
  final CheckStatus status;

  @override
  Widget build(BuildContext context) {
    final (color, icon) = switch (status) {
      CheckStatus.valid => (Colors.green, Icons.verified),
      CheckStatus.invalid => (Colors.red, Icons.dangerous),
      CheckStatus.notChecked => (Colors.grey, Icons.help_outline),
    };
    return Chip(
      avatar: Icon(icon, color: Colors.white, size: 18),
      label: Text(label, style: const TextStyle(color: Colors.white)),
      backgroundColor: color,
      side: BorderSide.none,
    );
  }
}

class _Section extends StatelessWidget {
  const _Section(this.title, this.children);

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 4),
            child: Text(title, style: Theme.of(context).textTheme.titleMedium),
          ),
          ...children,
        ]),
      ),
    );
  }
}

class _Field extends StatelessWidget {
  const _Field(this.label, this.value, {this.arabic});

  final String label;
  final String? value;
  final String? arabic;

  @override
  Widget build(BuildContext context) {
    if ((value == null || value!.isEmpty) && (arabic == null || arabic!.isEmpty)) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        SizedBox(width: 130, child: Text(label, style: Theme.of(context).textTheme.bodySmall)),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            if (value != null && value!.isNotEmpty) Text(value!),
            if (arabic != null && arabic!.isNotEmpty) _Arabic(arabic),
          ]),
        ),
      ]),
    );
  }
}

/// Texte arabe, de droite à gauche.
class _Arabic extends StatelessWidget {
  const _Arabic(this.text, {this.style});

  final String? text;
  final TextStyle? style;

  @override
  Widget build(BuildContext context) {
    if (text == null || text!.isEmpty) return const SizedBox.shrink();
    return Directionality(
      textDirection: TextDirection.rtl,
      child: SizedBox(width: double.infinity, child: Text(text!, style: style, textAlign: TextAlign.start)),
    );
  }
}
