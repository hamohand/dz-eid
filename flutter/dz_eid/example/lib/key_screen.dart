import 'package:dz_eid/dz_eid.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'read_screen.dart';

/// Clé d'accès : scan de la MRZ par la caméra, ou saisie manuelle.
class KeyScreen extends StatefulWidget {
  const KeyScreen({super.key});

  @override
  State<KeyScreen> createState() => _KeyScreenState();
}

class _KeyScreenState extends State<KeyScreen> {
  final _form = GlobalKey<FormState>();
  final _doc = TextEditingController();
  final _birth = TextEditingController();
  final _expiry = TextEditingController();
  bool _scanning = false;

  @override
  void dispose() {
    // Données personnelles : effacées dès que l'écran est quitté
    _doc.dispose();
    _birth.dispose();
    _expiry.dispose();
    super.dispose();
  }

  Future<void> _scan() async {
    setState(() => _scanning = true);
    try {
      final r = await DzEid.scanMrz();
      if (r == null || !mounted) return;
      _read(r.toAccessKey());
    } on DzEidException catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    } finally {
      if (mounted) setState(() => _scanning = false);
    }
  }

  void _submitManual() {
    if (!_form.currentState!.validate()) return;
    _read(AccessKey.fromDates(
      documentNumber: _doc.text.trim().toUpperCase(),
      dateOfBirth: _parseDate(_birth.text)!,
      dateOfExpiry: _parseDate(_expiry.text)!,
    ));
  }

  void _read(AccessKey key) {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => ReadScreen(accessKey: key)));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Clé d\'accès à la puce')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text('La puce est protégée : elle ne se lit qu\'avec les informations imprimées au dos de la carte '
              '(zone à trois lignes avec des <<<).'),
          const SizedBox(height: 20),
          FilledButton.icon(
            onPressed: _scanning ? null : _scan,
            icon: const Icon(Icons.document_scanner_outlined),
            label: const Padding(padding: EdgeInsets.all(12), child: Text('Scanner le dos de la carte')),
          ),
          const SizedBox(height: 28),
          Row(children: [
            const Expanded(child: Divider()),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Text('ou saisie manuelle', style: theme.textTheme.labelLarge),
            ),
            const Expanded(child: Divider()),
          ]),
          const SizedBox(height: 12),
          Form(
            key: _form,
            child: Column(children: [
              TextFormField(
                controller: _doc,
                decoration: const InputDecoration(labelText: 'Numéro de la carte', border: OutlineInputBorder()),
                textCapitalization: TextCapitalization.characters,
                inputFormatters: [FilteringTextInputFormatter.allow(RegExp('[A-Za-z0-9]')), LengthLimitingTextInputFormatter(22)],
                validator: (v) => (v == null || v.trim().isEmpty) ? 'Numéro obligatoire' : null,
              ),
              const SizedBox(height: 12),
              _DateField(controller: _birth, label: 'Date de naissance'),
              const SizedBox(height: 12),
              _DateField(controller: _expiry, label: 'Date d\'expiration'),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton(
                  onPressed: _submitManual,
                  child: const Padding(padding: EdgeInsets.all(12), child: Text('Lire la puce')),
                ),
              ),
            ]),
          ),
        ],
      ),
    );
  }
}

class _DateField extends StatelessWidget {
  const _DateField({required this.controller, required this.label});

  final TextEditingController controller;
  final String label;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      decoration: InputDecoration(labelText: label, hintText: 'JJ/MM/AAAA', border: const OutlineInputBorder()),
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly, _DateSlashFormatter()],
      validator: (v) => _parseDate(v ?? '') == null ? 'Date invalide (JJ/MM/AAAA)' : null,
    );
  }
}

/// JJ/MM/AAAA → DateTime, ou null si invalide.
DateTime? _parseDate(String text) {
  final m = RegExp(r'^(\d{2})/(\d{2})/(\d{4})$').firstMatch(text.trim());
  if (m == null) return null;
  final d = int.parse(m[1]!), mo = int.parse(m[2]!), y = int.parse(m[3]!);
  final date = DateTime(y, mo, d);
  return (date.day == d && date.month == mo && date.year == y) ? date : null;
}

/// Insère les « / » pendant la saisie : 12031985 → 12/03/1985.
class _DateSlashFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) {
    final digits = newValue.text.replaceAll('/', '');
    final clipped = digits.length > 8 ? digits.substring(0, 8) : digits;
    final b = StringBuffer();
    for (var i = 0; i < clipped.length; i++) {
      if (i == 2 || i == 4) b.write('/');
      b.write(clipped[i]);
    }
    final text = b.toString();
    return TextEditingValue(text: text, selection: TextSelection.collapsed(offset: text.length));
  }
}
