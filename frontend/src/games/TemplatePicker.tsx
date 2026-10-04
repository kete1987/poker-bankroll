import { Select } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { GameTemplate } from '../api/types';
import { useFormat } from '../format/useFormat';
import { templateLabel } from './templates';

interface TemplatePickerProps {
  /** The templates offered: those that can start games, of the types the form takes. */
  templates: GameTemplate[];
  /** A template was chosen: the form takes what it says. */
  onPick: (template: GameTemplate) => void;
}

/** "From template": fills a form for a new game with what a template says, in one choice. */
export function TemplatePicker({ templates, onPick }: TemplatePickerProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const [chosen, setChosen] = useState<string | null>(null);

  if (templates.length === 0) {
    return null;
  }
  return (
    <Select
      label={t('templates.from')}
      placeholder={t('templates.fromPlaceholder')}
      searchable
      data={templates.map((template) => ({
        value: String(template.id),
        label: templateLabel(t, format, template),
      }))}
      value={chosen}
      onChange={(value) => {
        setChosen(value);
        const template = templates.find((candidate) => String(candidate.id) === value);
        if (template) {
          onPick(template);
        }
      }}
    />
  );
}
