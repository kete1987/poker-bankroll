import { TagsInput, type TagsInputProps } from '@mantine/core';
import { useTranslation } from 'react-i18next';

import { MAX_TAG_LENGTH, MAX_TAGS, useTags } from '../api/tags';

/**
 * The tags of a game: the existing ones are suggested, and a new one is created by typing it
 * (Enter, or a comma). The backend matches them ignoring case.
 */
export function TagsField(props: Omit<TagsInputProps, 'data' | 'maxTags' | 'label'>) {
  const { t } = useTranslation();
  const tags = useTags();
  return (
    <TagsInput
      label={t('tags.label')}
      description={t('tags.help', { max: MAX_TAGS })}
      placeholder={props.value?.length ? undefined : t('tags.placeholder')}
      data={(tags.data ?? []).map((tag) => tag.name)}
      maxTags={MAX_TAGS}
      maxLength={MAX_TAG_LENGTH}
      // A semicolon separates tags in the CSV files: no tag can have one.
      splitChars={[',', ';']}
      clearable
      {...props}
    />
  );
}
