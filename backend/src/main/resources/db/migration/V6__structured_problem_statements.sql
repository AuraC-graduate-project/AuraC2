ALTER TABLE problems
    ADD COLUMN statement TEXT,
    ADD COLUMN input_format TEXT,
    ADD COLUMN output_format TEXT,
    ADD COLUMN constraints_text TEXT,
    ADD COLUMN public_notes TEXT,
    ADD COLUMN admin_notes TEXT;
