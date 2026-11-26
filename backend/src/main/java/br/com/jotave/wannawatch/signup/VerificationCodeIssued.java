package br.com.jotave.wannawatch.signup;

/** Um código novo foi gerado; o e-mail sai só depois que a transação confirma. */
record VerificationCodeIssued(String email, String displayName, String code) {}
