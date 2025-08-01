from feldera.rest.feldera_client import FelderaClient
from feldera.rest.pipeline import Pipeline as InnerPipeline
from feldera.pipeline import Pipeline
from feldera.enums import CompilationProfile
from feldera.runtime_config import RuntimeConfig
from feldera.rest.errors import FelderaAPIError


class PipelineBuilder:
    """
    A builder for creating a Feldera Pipeline.

    :param client: The :class:`.FelderaClient` instance
    :param name: The name of the pipeline
    :param description: The description of the pipeline
    :param sql: The SQL code of the pipeline
    :param udf_rust: Rust code for UDFs
    :param udf_toml: Rust dependencies required by UDFs (in the TOML format)
    :param compilation_profile: The :class:`.CompilationProfile` to use
    :param runtime_config: The :class:`.RuntimeConfig` to use. Enables
        configuring the runtime behavior of the pipeline such as:
        fault tolerance, storage and :class:`.Resources`
    """

    def __init__(
        self,
        client: FelderaClient,
        name: str,
        sql: str,
        udf_rust: str = "",
        udf_toml: str = "",
        description: str = "",
        compilation_profile: CompilationProfile = CompilationProfile.OPTIMIZED,
        runtime_config: RuntimeConfig = RuntimeConfig.default(),
    ):
        self.client: FelderaClient = client
        self.name: str | None = name
        self.description: str = description
        self.sql: str = sql
        self.udf_rust: str = udf_rust
        self.udf_toml: str = udf_toml
        self.compilation_profile: CompilationProfile = compilation_profile
        self.runtime_config: RuntimeConfig = runtime_config

    def create(self) -> Pipeline:
        """
        Create the pipeline if it does not exist.

        :return: The created pipeline
        """

        if self.name is None or self.sql is None:
            raise ValueError("Name and SQL are required to create a pipeline")

        try:
            if self.client.get_pipeline(self.name) is not None:
                raise RuntimeError(f"Pipeline with name {self.name} already exists")
        except FelderaAPIError as err:
            if err.error_code != "UnknownPipelineName":
                raise err

        inner = InnerPipeline(
            self.name,
            description=self.description,
            sql=self.sql,
            udf_rust=self.udf_rust,
            udf_toml=self.udf_toml,
            program_config={
                "profile": self.compilation_profile.value,
            },
            runtime_config=dict(
                (k, v) for k, v in self.runtime_config.__dict__.items() if v is not None
            ),
        )

        inner = self.client.create_pipeline(inner)
        pipeline = Pipeline(self.client)
        pipeline._inner = inner

        return pipeline

    def create_or_replace(self) -> Pipeline:
        """
        Creates a pipeline if it does not exist and replaces it if it exists.

        If the pipeline exists and is running, it will be stopped and replaced.
        """

        if self.name is None or self.sql is None:
            raise ValueError("Name and SQL are required to create a pipeline")

        try:
            # shutdown the pipeline if it exists and is running
            p = Pipeline.get(self.name, self.client)
            p.stop(force=True)
            p.clear_storage()

        except FelderaAPIError:
            # pipeline doesn't exist, no worries
            pass

        inner = InnerPipeline(
            self.name,
            description=self.description,
            sql=self.sql,
            udf_rust=self.udf_rust,
            udf_toml=self.udf_toml,
            program_config={
                "profile": self.compilation_profile.value,
            },
            runtime_config=dict(
                (k, v) for k, v in self.runtime_config.__dict__.items() if v is not None
            ),
        )

        inner = self.client.create_or_update_pipeline(inner)
        pipeline = Pipeline(self.client)
        pipeline._inner = inner

        return pipeline
