# Forms

> The mandatory react-hook-form + zod + shadcn `Field` pattern and its references. Part of the [frontend guide](README.md).

## Forms (mandatory)

Every new form, and every existing form you substantially change, uses **react-hook-form + a zod schema + shadcn `Field`**. Don't validate with `useState`, hand-written `validate()` functions or `FieldErrors` objects. References: `ResetPasswordForm` + `useResetPasswordForm` (`schemas/auth.ts`, client-only confirmation with `.refine`), `CreateSessionDialog` (`schemas/daily.ts`, zod input/output types for a nullable date) and `ProfileCard` + `useProfileSettings` (`schemas/user.ts`, `schemas/upload.ts`). The pelada settings cards (`components/pelada-settings/`, `schemas/pelada.ts`) are the reference for a page of independent forms: one `useForm` per card, a cross-field `superRefine` (`peladaSlotsSchema`: the mensalista limit against the capacity being edited), a file kept outside the form (`InfoCard`) and client-only typed-name confirmations (`makeDeletePeladaSchema`, `makeTransferPeladaSchema`). `CreatePeladaSheet` + `useCreatePeladaForm` (`createPeladaSchema`, composed from the settings schemas) is the reference for a create form with an optional file: the photo goes after the record is created, and its failure only warns, since the record already exists. `ImportFromMessageModal` + `useImportFromMessage` is the reference for a two-step flow: one `useForm` per step in the same hook (`importMessageSchema`, then `importResolutionSchema` seeded with `reset` from the parse), the footer's submit buttons tied to each native form through `form="…"`. `ResultsDialog` (`useResultsForm`) is the reference for a form with a field array.

Rules:

1. **One schema per form in `src/schemas/<domain>.ts`**, copying every constraint of the backend request DTO (`@NotBlank` → `.min(1)`, `@Size(min = 3, max = 30)` → `.min(3).max(30)`, `@Min(0)` → `.min(0)`, `@Email` → `.email()`), messages in Portuguese, with a comment naming the DTO it mirrors. Derive the type with `z.infer`.
2. **Keep schemas in sync with the backend**: a DTO change updates its schema in the same change.
3. **`useForm` + `zodResolver(schema)`** with complete `defaultValues` (never `undefined`).
4. **Fields go through `Controller` + `Field`**: `data-invalid` on `Field`, `aria-invalid` on the control, `FieldLabel htmlFor` matching the control `id`, `FieldError` for the message, correct `type`/`autoComplete`.
5. **Native `<form onSubmit={form.handleSubmit(onSubmit)} noValidate>`**, submit `Button type="submit" disabled={form.formState.isSubmitting}`. Use `formState.isDirty`/`isSubmitting` and `form.reset(values)` instead of extra `isLoading`/`hasChanges` state.
6. **Map backend errors**: the validation 400 body is `{status, message, timestamp, errors: {field: message}}`; other errors are `{status, message, timestamp}`. Put field errors on the fields and the rest on `root`, shown in an `Alert variant="destructive"`, with `applyServerErrors(form, error, fallback)` (`lib/form-errors.ts`); outside forms use `getErrorMessage(error, fallback)` (`lib/errors.ts`) — never per-component casts.
7. **File inputs** validate size (5 MB) and type (`image/jpeg`, `image/png`, `image/webp`) with `schemas/upload.ts` (`validateImageFile`, `IMAGE_ACCEPT`), matching `FileUploadService`.

The profile card, as it is in the code (`schemas/user.ts`, `components/account-settings/hooks/useProfileSettings.ts`, `ProfileCard.tsx`):

```ts
// src/schemas/user.ts
import { z } from "@/lib/zod"

// mirrors UpdateProfileRequest ("" clears the position)
export const profileSchema = z.object({
  username: z.string().trim().min(3, "Mínimo de 3 caracteres").max(30, "Máximo de 30 caracteres"),
  position: z.union([z.enum(EDITABLE_POSITIONS), z.literal("")]),
})
export type ProfileValues = z.infer<typeof profileSchema>
```

```ts
// useProfileSettings: the hook owns the form and the submit
const form = useForm<ProfileValues>({
  resolver: zodResolver(profileSchema),
  defaultValues: { username: user.username, position: toFormPosition(user.position) },
})

const onSubmit = form.handleSubmit(async (values) => {
  try {
    const updated = await updateUser(user.id, values)
    form.reset({ username: updated.username, position: toFormPosition(updated.position) })
    updateSessionUser(sessionPatch(updated))
    toast.success("Perfil atualizado")
  } catch (error) {
    applyServerErrors(form, error, "Não foi possível salvar o perfil")
  }
})
```

```tsx
// ProfileCard: the component only renders
<Controller
  name="username"
  control={form.control}
  render={({ field, fieldState }) => (
    <Field data-invalid={fieldState.invalid}>
      <FieldLabel htmlFor="profile-username">Nome de usuário</FieldLabel>
      <Input {...field} id="profile-username" autoComplete="username" aria-invalid={fieldState.invalid} />
      {fieldState.error ? (
        <FieldError errors={[fieldState.error]} />
      ) : (
        <FieldDescription>De 3 a 30 caracteres.</FieldDescription>
      )}
    </Field>
  )}
/>
```

Stars are not on the profile: admins set them per pelada (`SetPlayerStarsDialog`).

## Antipatterns

Don't introduce these. Numbers are shared by the whole frontend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 5 | Forms with `useState` per field and hand-written validation | react-hook-form + zod + `Field` ([Forms](forms.md#forms-mandatory)) | — |
